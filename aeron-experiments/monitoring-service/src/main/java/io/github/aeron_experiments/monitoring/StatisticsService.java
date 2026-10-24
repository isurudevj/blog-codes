package io.github.aeron_experiments.monitoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.aeron.AeronCounters;
import io.github.aeron_experiments.common.ApplicationCounters;
import io.github.aeron_experiments.monitoring.pojo.MediaDriverState;
import io.github.aeron_experiments.monitoring.pojo.MonitoringState;
import io.github.aeron_experiments.monitoring.pojo.PublisherInfo;
import io.github.aeron_experiments.monitoring.pojo.StreamInfo;
import io.github.aeron_experiments.monitoring.pojo.SubscriberInfo;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.status.CountersReader;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StatisticsService {

    private static final int REGISTRATION_ID_OFFSET = 0;
    private static final int SESSION_ID_OFFSET = REGISTRATION_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int STREAM_ID_OFFSET = SESSION_ID_OFFSET + BitUtil.SIZE_OF_INT;
    private static final int CHANNEL_OFFSET = STREAM_ID_OFFSET + BitUtil.SIZE_OF_INT;

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final Map<PositionKey, PositionSample> previousPositions = new HashMap<>();
    private MonitoringState currentState;

    public StatisticsService() {
        this(Clock.systemUTC());
    }

    StatisticsService(final Clock clock) {
        this.clock = clock;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.currentState = downState(clock.instant(), null);
    }

    public void update() {
        previousPositions.clear();
        currentState = downState(clock.instant(), null);
    }

    public void update(final CountersReader reader, final long heartbeatAgeMs) {
        final Instant observedAt = clock.instant();
        final long observedAtMs = observedAt.toEpochMilli();
        final Long2ObjectHashMap<String> applicationNames = readApplicationNames(reader);
        final Map<StreamKey, StreamBuilder> streams = new HashMap<>();
        final Set<PositionKey> observedPositions = new HashSet<>();

        reader.forEach((counterId, typeId, keyBuffer, label) -> {
            if (AeronCounters.DRIVER_PUBLISHER_POS_TYPE_ID != typeId &&
                AeronCounters.DRIVER_SUBSCRIBER_POSITION_TYPE_ID != typeId) {
                return;
            }

            final StreamKey streamKey = readStreamKey(keyBuffer);
            if (!streamKey.channel().startsWith("aeron:ipc")) {
                return;
            }

            final long registrationId = reader.getCounterRegistrationId(counterId);
            final long ownerId = reader.getCounterOwnerId(counterId);
            final long position = reader.getCounterValue(counterId);
            final PositionKey positionKey = new PositionKey(typeId, registrationId, ownerId, streamKey);
            final double rate = calculateRate(positionKey, position, observedAtMs);
            observedPositions.add(positionKey);

            final String application = applicationNames.getOrDefault(ownerId, "untracked-app");
            final StreamBuilder stream = streams.computeIfAbsent(streamKey, StreamBuilder::new);
            if (AeronCounters.DRIVER_PUBLISHER_POS_TYPE_ID == typeId) {
                stream.publisher = new PublisherInfo(application, registrationId, ownerId, position, rate);
            } else {
                stream.subscribers.add(new SubscriberSample(
                    application, registrationId, ownerId, position, rate));
            }
        });

        previousPositions.keySet().retainAll(observedPositions);
        final List<StreamInfo> streamStates = streams.values().stream()
            .filter(stream -> stream.publisher != null)
            .map(StreamBuilder::build)
            .sorted(Comparator.comparingInt(StreamInfo::getStreamId).thenComparingInt(StreamInfo::getSessionId))
            .toList();

        currentState = new MonitoringState(
            observedAt.toString(), new MediaDriverState(true, heartbeatAgeMs), streamStates);
    }

    public String state() {
        try {
            return objectMapper.writeValueAsString(currentState);
        } catch (final JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize monitoring state", ex);
        }
    }

    private Long2ObjectHashMap<String> readApplicationNames(final CountersReader reader) {
        final Long2ObjectHashMap<String> result = new Long2ObjectHashMap<>();
        reader.forEach((counterId, typeId, keyBuffer, label) -> {
            if (ApplicationCounters.APPLICATION_IDENTITY_TYPE_ID == typeId) {
                final String prefix = ApplicationCounters.APPLICATION_IDENTITY_LABEL_PREFIX;
                final String applicationName = label.startsWith(prefix) ? label.substring(prefix.length()) : label;
                result.put(reader.getCounterOwnerId(counterId), applicationName);
            }
        });
        return result;
    }

    private double calculateRate(final PositionKey key, final long position, final long observedAtMs) {
        final PositionSample previous = previousPositions.put(key, new PositionSample(position, observedAtMs));
        if (previous == null || position < previous.position() || observedAtMs <= previous.observedAtMs()) {
            return 0.0;
        }
        return (position - previous.position()) * 1_000.0 / (observedAtMs - previous.observedAtMs());
    }

    private static StreamKey readStreamKey(final DirectBuffer keyBuffer) {
        return new StreamKey(
            keyBuffer.getStringAscii(CHANNEL_OFFSET),
            keyBuffer.getInt(STREAM_ID_OFFSET),
            keyBuffer.getInt(SESSION_ID_OFFSET));
    }

    private static MonitoringState downState(final Instant observedAt, final Long heartbeatAgeMs) {
        return new MonitoringState(
            observedAt.toString(), new MediaDriverState(false, heartbeatAgeMs), List.of());
    }

    private record StreamKey(String channel, int streamId, int sessionId) {
    }

    private record PositionKey(int typeId, long registrationId, long ownerId, StreamKey streamKey) {
    }

    private record PositionSample(long position, long observedAtMs) {
    }

    private record SubscriberSample(
        String application,
        long registrationId,
        long ownerId,
        long position,
        double rate) {
    }

    private static final class StreamBuilder {
        private final StreamKey key;
        private PublisherInfo publisher;
        private final List<SubscriberSample> subscribers = new ArrayList<>();

        private StreamBuilder(final StreamKey key) {
            this.key = key;
        }

        private StreamInfo build() {
            final List<SubscriberInfo> subscriberStates = subscribers.stream()
                .map(subscriber -> new SubscriberInfo(
                    subscriber.application(),
                    subscriber.registrationId(),
                    subscriber.ownerId(),
                    subscriber.position(),
                    subscriber.rate(),
                    Math.max(0, publisher.getPositionBytes() - subscriber.position())))
                .toList();
            return new StreamInfo(
                key.channel(), key.streamId(), key.sessionId(), publisher, subscriberStates);
        }
    }
}
