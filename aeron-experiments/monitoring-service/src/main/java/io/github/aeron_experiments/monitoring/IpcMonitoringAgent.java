package io.github.aeron_experiments.monitoring;

import io.aeron.AeronCounters;
import io.github.aeron_experiments.common.ApplicationCounters;
import org.agrona.BitUtil;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.status.CountersReader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import static org.agrona.concurrent.status.CountersReader.KEY_OFFSET;
import static org.agrona.concurrent.status.CountersReader.RECORD_ALLOCATED;

final class IpcMonitoringAgent implements Agent {
    private static final int REGISTRATION_ID_OFFSET = 0;
    private static final int SESSION_ID_OFFSET = REGISTRATION_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int STREAM_ID_OFFSET = SESSION_ID_OFFSET + BitUtil.SIZE_OF_INT;
    private static final int CHANNEL_OFFSET = STREAM_ID_OFFSET + BitUtil.SIZE_OF_INT;
    private static final String IPC_CHANNEL = "aeron:ipc";

    private final CountersReader counters;
    private final long intervalMs;
    private final BlockingQueue<MonitoringStatistics> statisticsQueue;
    private final AtomicLong droppedStatistics;
    private final Map<Integer, PreviousValue> previousValues = new HashMap<>();
    private final Map<StreamKey, Long> constrainedSinceMs = new HashMap<>();
    private long previousSampleMs;
    private long nextSampleMs;

    IpcMonitoringAgent(
        final CountersReader counters,
        final long intervalMs,
        final BlockingQueue<MonitoringStatistics> statisticsQueue,
        final AtomicLong droppedStatistics) {
        this.counters = counters;
        this.intervalMs = intervalMs;
        this.statisticsQueue = statisticsQueue;
        this.droppedStatistics = droppedStatistics;
    }

    @Override
    public int doWork() {
        final long nowMs = System.currentTimeMillis();
        if (nowMs < nextSampleMs) {
            return 0;
        }

        final long elapsedMs = 0 == previousSampleMs ? 0 : Math.max(1, nowMs - previousSampleMs);
        previousSampleMs = nowMs;
        nextSampleMs = nowMs + intervalMs;
        publish(collect(nowMs, elapsedMs));
        return 1;
    }

    @Override
    public String roleName() {
        return "aeron-ipc-monitor";
    }

    private MonitoringStatistics collect(final long nowMs, final long elapsedMs) {
        final Map<StreamKey, Publication> publications = new HashMap<>();
        final List<Subscriber> subscribers = new ArrayList<>();
        final Map<Integer, Long> systemCounters = new HashMap<>();
        final Map<Long, String> applicationNames = new HashMap<>();
        final AtomicBuffer metadata = counters.metaDataBuffer();

        for (int counterId = 0, maxCounterId = counters.maxCounterId(); counterId <= maxCounterId; counterId++) {
            if (RECORD_ALLOCATED != counters.getCounterState(counterId)) {
                continue;
            }

            final int typeId = counters.getCounterTypeId(counterId);
            final long value = counters.getCounterValue(counterId);
            if (ApplicationCounters.APPLICATION_IDENTITY_TYPE_ID == typeId) {
                applicationNames.put(counters.getCounterOwnerId(counterId), applicationName(counterId));
            } else if (AeronCounters.DRIVER_SYSTEM_COUNTER_TYPE_ID == typeId) {
                final int keyOffset = CountersReader.metaDataOffset(counterId) + KEY_OFFSET;
                systemCounters.put(metadata.getInt(keyOffset), value);
            } else if (isIpcPosition(typeId)) {
                final StreamMetadata streamMetadata = readStreamMetadata(metadata, counterId);
                if (!IPC_CHANNEL.equals(streamMetadata.channel())) {
                    continue;
                }

                final StreamKey key = new StreamKey(
                    streamMetadata.sessionId(), streamMetadata.streamId(), streamMetadata.channel());
                final long ownerId = counters.getCounterOwnerId(counterId);
                if (AeronCounters.DRIVER_SUBSCRIBER_POSITION_TYPE_ID == typeId) {
                    subscribers.add(new Subscriber(
                        counterId, ownerId, streamMetadata.registrationId(), key, value));
                } else {
                    final Publication publication = publications.computeIfAbsent(key, Publication::new);
                    if (AeronCounters.DRIVER_PUBLISHER_POS_TYPE_ID == typeId) {
                        publication.positionCounterId = counterId;
                        publication.ownerId = ownerId;
                        publication.position = value;
                    } else {
                        publication.limit = value;
                        publication.hasLimit = true;
                    }
                }
            }
        }

        final List<MonitoringStatistics.PublicationStatistics> publicationStatistics = publications.values().stream()
            .filter(publication -> publication.positionCounterId >= 0)
            .sorted(Comparator.comparingInt(publication -> publication.key.streamId()))
            .map(publication -> publicationStatistics(
                publication, subscribers, applicationNames, nowMs, elapsedMs))
            .toList();
        final List<MonitoringStatistics.SubscriberStatistics> subscriberStatistics = subscribers.stream()
            .sorted(Comparator.comparingInt(subscriber -> subscriber.key.streamId()))
            .map(subscriber -> subscriberStatistics(
                subscriber, publications.get(subscriber.key), applicationNames, elapsedMs))
            .toList();

        previousValues.keySet().removeIf(counterId ->
            counterId > counters.maxCounterId() || RECORD_ALLOCATED != counters.getCounterState(counterId));
        constrainedSinceMs.keySet().retainAll(publications.keySet());

        return new MonitoringStatistics(
            nowMs,
            publicationStatistics,
            subscriberStatistics,
            new MonitoringStatistics.DriverStatistics(
                systemCounter(systemCounters, AeronCounters.SYSTEM_COUNTER_ID_ERRORS),
                systemCounter(systemCounters, AeronCounters.SYSTEM_COUNTER_ID_UNBLOCKED_PUBLICATIONS),
                systemCounter(systemCounters, AeronCounters.SYSTEM_COUNTER_ID_CLIENT_TIMEOUTS),
                systemCounter(systemCounters, AeronCounters.SYSTEM_COUNTER_ID_PUBLICATIONS_REVOKED),
                systemCounter(systemCounters,
                    AeronCounters.SYSTEM_COUNTER_ID_CONDUCTOR_CYCLE_TIME_THRESHOLD_EXCEEDED),
                systemCounter(systemCounters, AeronCounters.SYSTEM_COUNTER_ID_CONDUCTOR_MAX_CYCLE_TIME)));
    }

    private MonitoringStatistics.PublicationStatistics publicationStatistics(
        final Publication publication,
        final List<Subscriber> subscribers,
        final Map<Long, String> applicationNames,
        final long nowMs,
        final long elapsedMs) {
        final List<Subscriber> matchingSubscribers = subscribers.stream()
            .filter(subscriber -> subscriber.key.equals(publication.key))
            .toList();
        final long maximumLag = matchingSubscribers.stream()
            .mapToLong(subscriber -> Math.max(0, publication.position - subscriber.position))
            .max().orElse(0);
        final long window = publication.hasLimit ? publication.limit - publication.position : 0;
        final boolean constrained = publication.hasLimit && window <= 0 && !matchingSubscribers.isEmpty();
        final long constrainedDurationMs;
        if (constrained) {
            constrainedDurationMs = nowMs - constrainedSinceMs.computeIfAbsent(publication.key, ignored -> nowMs);
        } else {
            constrainedSinceMs.remove(publication.key);
            constrainedDurationMs = 0;
        }

        return new MonitoringStatistics.PublicationStatistics(
            displayName(applicationNames, publication.ownerId),
            publication.key.streamId(),
            publication.key.sessionId(),
            publication.position,
            rate(publication.positionCounterId, publication.position, elapsedMs),
            window,
            maximumLag,
            constrained,
            constrainedDurationMs / 1_000.0);
    }

    private MonitoringStatistics.SubscriberStatistics subscriberStatistics(
        final Subscriber subscriber,
        final Publication publication,
        final Map<Long, String> applicationNames,
        final long elapsedMs) {
        final long lag = null == publication || publication.positionCounterId < 0 ? 0 :
            Math.max(0, publication.position - subscriber.position);
        return new MonitoringStatistics.SubscriberStatistics(
            displayName(applicationNames, subscriber.ownerId),
            subscriber.key.streamId(),
            subscriber.key.sessionId(),
            subscriber.registrationId,
            subscriber.position,
            rate(subscriber.counterId, subscriber.position, elapsedMs),
            lag,
            lag > 0);
    }

    private void publish(final MonitoringStatistics statistics) {
        if (!statisticsQueue.offer(statistics)) {
            statisticsQueue.poll();
            droppedStatistics.incrementAndGet();
            statisticsQueue.offer(statistics);
        }
    }

    private double rate(final int counterId, final long value, final long elapsedMs) {
        final PreviousValue previous = previousValues.put(counterId, new PreviousValue(value));
        if (null == previous || elapsedMs <= 0) {
            return 0;
        }
        return Math.max(0, value - previous.value) * 1_000.0 / elapsedMs;
    }

    private String applicationName(final int counterId) {
        final String label = counters.getCounterLabel(counterId);
        return label.startsWith(ApplicationCounters.APPLICATION_IDENTITY_LABEL_PREFIX) ?
            label.substring(ApplicationCounters.APPLICATION_IDENTITY_LABEL_PREFIX.length()) : label;
    }

    private static String displayName(final Map<Long, String> applicationNames, final long ownerId) {
        return applicationNames.getOrDefault(ownerId, "client-" + ownerId);
    }

    private static long systemCounter(final Map<Integer, Long> counters, final int counterId) {
        return counters.getOrDefault(counterId, 0L);
    }

    private static boolean isIpcPosition(final int typeId) {
        return AeronCounters.DRIVER_PUBLISHER_POS_TYPE_ID == typeId ||
            AeronCounters.DRIVER_PUBLISHER_LIMIT_TYPE_ID == typeId ||
            AeronCounters.DRIVER_SUBSCRIBER_POSITION_TYPE_ID == typeId;
    }

    private static StreamMetadata readStreamMetadata(final AtomicBuffer metadata, final int counterId) {
        final int keyOffset = CountersReader.metaDataOffset(counterId) + KEY_OFFSET;
        final long registrationId = metadata.getLong(keyOffset + REGISTRATION_ID_OFFSET);
        final int sessionId = metadata.getInt(keyOffset + SESSION_ID_OFFSET);
        final int streamId = metadata.getInt(keyOffset + STREAM_ID_OFFSET);
        final int channelLength = metadata.getInt(keyOffset + CHANNEL_OFFSET);
        final String channel = metadata.getStringWithoutLengthAscii(
            keyOffset + CHANNEL_OFFSET + BitUtil.SIZE_OF_INT, channelLength);
        return new StreamMetadata(registrationId, sessionId, streamId, channel);
    }

    private record StreamKey(int sessionId, int streamId, String channel) { }
    private record StreamMetadata(long registrationId, int sessionId, int streamId, String channel) { }
    private record Subscriber(int counterId, long ownerId, long registrationId, StreamKey key, long position) { }
    private record PreviousValue(long value) { }

    private static final class Publication {
        private final StreamKey key;
        private int positionCounterId = -1;
        private long ownerId;
        private long position;
        private long limit;
        private boolean hasLimit;

        private Publication(final StreamKey key) {
            this.key = key;
        }
    }
}
