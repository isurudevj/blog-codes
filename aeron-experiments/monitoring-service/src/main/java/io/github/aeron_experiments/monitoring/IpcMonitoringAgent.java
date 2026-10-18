package io.github.aeron_experiments.monitoring;

import io.aeron.AeronCounters;
import io.github.aeron_experiments.common.ApplicationCounters;
import lombok.extern.slf4j.Slf4j;
import org.agrona.BitUtil;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.status.CountersReader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.agrona.concurrent.status.CountersReader.KEY_OFFSET;
import static org.agrona.concurrent.status.CountersReader.RECORD_ALLOCATED;

@Slf4j
final class IpcMonitoringAgent implements Agent {
    private static final int REGISTRATION_ID_OFFSET = 0;
    private static final int SESSION_ID_OFFSET = REGISTRATION_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int STREAM_ID_OFFSET = SESSION_ID_OFFSET + BitUtil.SIZE_OF_INT;
    private static final int CHANNEL_OFFSET = STREAM_ID_OFFSET + BitUtil.SIZE_OF_INT;
    private static final String IPC_CHANNEL = "aeron:ipc";

    private final CountersReader counters;
    private final long intervalMs;
    private final PrometheusMetricsServer metricsServer;
    private final Map<Integer, PreviousValue> previousValues = new HashMap<>();
    private final Map<StreamKey, Long> constrainedSinceMs = new HashMap<>();
    private long nextSampleMs;

    IpcMonitoringAgent(
        final CountersReader counters,
        final long intervalMs,
        final PrometheusMetricsServer metricsServer) {
        this.counters = counters;
        this.intervalMs = intervalMs;
        this.metricsServer = metricsServer;
    }

    public int doWork() {
        final long nowMs = System.currentTimeMillis();
        if (nowMs < nextSampleMs) {
            return 0;
        }

        final long elapsedMs = 0 == nextSampleMs ? 0 : Math.max(1, intervalMs + nowMs - nextSampleMs);
        nextSampleMs = nowMs + intervalMs;
        log.info("\n{}", buildView(nowMs, elapsedMs));
        return 1;
    }

    public String roleName() {
        return "aeron-ipc-monitor";
    }

    private String buildView(final long nowMs, final long elapsedMs) {
        final Map<StreamKey, Publication> publications = new HashMap<>();
        final List<Subscriber> subscribers = new ArrayList<>();
        final Map<Integer, CounterValue> systemCounters = new HashMap<>();
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
                systemCounters.put(metadata.getInt(keyOffset), new CounterValue(counterId, value));
            } else if (isIpcStreamPosition(typeId)) {
                final StreamMetadata streamMetadata = readStreamMetadata(metadata, counterId);
                if (!IPC_CHANNEL.equals(streamMetadata.channel())) {
                    continue;
                }

                final StreamKey streamKey = new StreamKey(
                    streamMetadata.sessionId(), streamMetadata.streamId(), streamMetadata.channel());
                final long ownerId = counters.getCounterOwnerId(counterId);
                if (AeronCounters.DRIVER_SUBSCRIBER_POSITION_TYPE_ID == typeId) {
                    subscribers.add(new Subscriber(
                        counterId, ownerId, streamMetadata.registrationId(), streamKey, value));
                } else {
                    final Publication publication = publications.computeIfAbsent(streamKey, Publication::new);
                    if (AeronCounters.DRIVER_PUBLISHER_POS_TYPE_ID == typeId) {
                        publication.positionCounterId = counterId;
                        publication.ownerId = ownerId;
                        publication.registrationId = streamMetadata.registrationId();
                        publication.position = value;
                    } else {
                        publication.limit = value;
                        publication.hasLimit = true;
                    }
                }
            }
        }

        final StringBuilder view = new StringBuilder(2_048);
        final Map<Integer, Double> sampleRates = new HashMap<>();
        publications.values().stream()
            .filter(publication -> publication.positionCounterId >= 0)
            .forEach(publication -> sampleRates.put(
                publication.positionCounterId,
                rate(publication.positionCounterId, publication.position, elapsedMs)));
        subscribers.forEach(subscriber -> sampleRates.put(
            subscriber.counterId, rate(subscriber.counterId, subscriber.position, elapsedMs)));
        view.append("AERON IPC MONITOR  time=").append(Instant.ofEpochMilli(nowMs))
            .append(" interval=").append(intervalMs / 1_000.0).append("s\n");
        view.append("PUBLICATIONS\n");
        view.append(String.format(Locale.ROOT,
            "%-20s %-7s %-11s %14s %12s %12s %12s %s%n",
            "APP", "STREAM", "SESSION", "POSITION", "RATE", "WINDOW", "MAX-LAG", "STATE"));

        final List<Publication> sortedPublications = publications.values().stream()
            .filter(publication -> publication.positionCounterId >= 0)
            .sorted(Comparator.comparingInt(publication -> publication.key.streamId()))
            .toList();
        if (sortedPublications.isEmpty()) {
            view.append("(no IPC publications)\n");
        }

        for (final Publication publication : sortedPublications) {
            final List<Subscriber> matchingSubscribers = subscribers.stream()
                .filter(subscriber -> subscriber.key.equals(publication.key))
                .toList();
            final long maxLag = matchingSubscribers.stream()
                .mapToLong(subscriber -> Math.max(0, publication.position - subscriber.position))
                .max().orElse(0);
            final long window = publication.hasLimit ? publication.limit - publication.position : Long.MIN_VALUE;
            final String state = publicationState(publication, matchingSubscribers, window, nowMs);
            view.append(String.format(Locale.ROOT,
                "%-20s %-7d %-11d %14s %12s %12s %12s %s%n",
                displayName(applicationNames, publication.ownerId),
                publication.key.streamId(), publication.key.sessionId(), formatBytes(publication.position),
                formatRate(sampleRates.get(publication.positionCounterId)),
                publication.hasLimit ? formatBytes(window) : "n/a", formatBytes(maxLag), state));
        }

        view.append("SUBSCRIBERS\n");
        view.append(String.format(Locale.ROOT,
            "%-20s %-7s %-11s %-12s %14s %12s %12s %s%n",
            "APP", "STREAM", "SESSION", "REG-ID", "POSITION", "RATE", "LAG", "STATE"));
        subscribers.sort(Comparator.comparingInt(subscriber -> subscriber.key.streamId()));
        if (subscribers.isEmpty()) {
            view.append("(no IPC subscribers)\n");
        }
        for (final Subscriber subscriber : subscribers) {
            final Publication publication = publications.get(subscriber.key);
            final long lag = null == publication || publication.positionCounterId < 0 ? Long.MIN_VALUE :
                Math.max(0, publication.position - subscriber.position);
            view.append(String.format(Locale.ROOT,
                "%-20s %-7d %-11d %-12d %14s %12s %12s %s%n",
                displayName(applicationNames, subscriber.ownerId),
                subscriber.key.streamId(), subscriber.key.sessionId(), subscriber.registrationId,
                formatBytes(subscriber.position), formatRate(sampleRates.get(subscriber.counterId)),
                Long.MIN_VALUE == lag ? "n/a" : formatBytes(lag),
                null == publication || publication.positionCounterId < 0 ? "NO_PUBLICATION" :
                    lag > 0 ? "LAGGING" : "CONNECTED"));
        }

        appendHealth(view, systemCounters, elapsedMs);
        metricsServer.update(buildPrometheusMetrics(
            nowMs, sortedPublications, subscribers, publications, applicationNames, systemCounters, sampleRates));
        previousValues.keySet().removeIf(counterId ->
            counterId > counters.maxCounterId() || RECORD_ALLOCATED != counters.getCounterState(counterId));
        return view.toString().stripTrailing();
    }

    private String publicationState(
        final Publication publication, final List<Subscriber> subscribers, final long window, final long nowMs) {
        if (subscribers.isEmpty()) {
            constrainedSinceMs.remove(publication.key);
            return "NO_SUBSCRIBER";
        }
        if (publication.hasLimit && window <= 0) {
            final long sinceMs = constrainedSinceMs.computeIfAbsent(publication.key, ignored -> nowMs);
            return "CONSTRAINED " + formatDuration(nowMs - sinceMs);
        }
        constrainedSinceMs.remove(publication.key);
        return "CONNECTED";
    }

    private void appendHealth(
        final StringBuilder view, final Map<Integer, CounterValue> systemCounters, final long elapsedMs) {
        view.append("DRIVER HEALTH\n  ");
        appendHealthCounter(view, "errors", AeronCounters.SYSTEM_COUNTER_ID_ERRORS, systemCounters, elapsedMs);
        appendHealthCounter(view, "unblocked-publications",
            AeronCounters.SYSTEM_COUNTER_ID_UNBLOCKED_PUBLICATIONS, systemCounters, elapsedMs);
        appendHealthCounter(view, "client-timeouts",
            AeronCounters.SYSTEM_COUNTER_ID_CLIENT_TIMEOUTS, systemCounters, elapsedMs);
        appendHealthCounter(view, "publications-revoked",
            AeronCounters.SYSTEM_COUNTER_ID_PUBLICATIONS_REVOKED, systemCounters, elapsedMs);
        appendHealthCounter(view, "conductor-threshold-exceeded",
            AeronCounters.SYSTEM_COUNTER_ID_CONDUCTOR_CYCLE_TIME_THRESHOLD_EXCEEDED, systemCounters, elapsedMs);

        final CounterValue maxCycle = systemCounters.get(AeronCounters.SYSTEM_COUNTER_ID_CONDUCTOR_MAX_CYCLE_TIME);
        if (null != maxCycle) {
            view.append("conductor-max-cycle=").append(formatDurationNanos(maxCycle.value)).append(' ');
            remember(maxCycle.counterId, maxCycle.value);
        }
    }

    private String buildPrometheusMetrics(
        final long nowMs,
        final List<Publication> publications,
        final List<Subscriber> subscribers,
        final Map<StreamKey, Publication> publicationsByStream,
        final Map<Long, String> applicationNames,
        final Map<Integer, CounterValue> systemCounters,
        final Map<Integer, Double> sampleRates) {
        final StringBuilder output = new StringBuilder(4_096);
        metricHeader(output, "aeron_monitor_ready", "Whether the first CnC sample has been collected.");
        output.append("aeron_monitor_ready 1\n");
        metricHeader(output, "aeron_monitor_snapshot_timestamp_seconds", "Unix time of the latest CnC sample.");
        output.append("aeron_monitor_snapshot_timestamp_seconds ").append(nowMs / 1_000.0).append('\n');

        metricHeader(output, "aeron_ipc_publisher_position_bytes", "Current IPC publisher position in bytes.");
        metricHeader(output, "aeron_ipc_publisher_rate_bytes_per_second", "IPC publisher rate over the sample interval.");
        metricHeader(output, "aeron_ipc_publisher_window_bytes", "Available IPC publication window in bytes.");
        metricHeader(output, "aeron_ipc_publisher_max_subscriber_lag_bytes", "Maximum subscriber lag for the publication.");
        metricHeader(output, "aeron_ipc_publisher_constrained", "Whether the IPC publication window is exhausted.");
        metricHeader(output, "aeron_ipc_publisher_constrained_duration_seconds", "Observed duration of publication constraint.");
        for (final Publication publication : publications) {
            final String labels = streamLabels(
                displayName(applicationNames, publication.ownerId), publication.key);
            final List<Subscriber> matchingSubscribers = subscribers.stream()
                .filter(subscriber -> subscriber.key.equals(publication.key)).toList();
            final long maxLag = matchingSubscribers.stream()
                .mapToLong(subscriber -> Math.max(0, publication.position - subscriber.position))
                .max().orElse(0);
            final long window = publication.hasLimit ? publication.limit - publication.position : 0;
            final boolean constrained = publication.hasLimit && window <= 0 && !matchingSubscribers.isEmpty();
            final Long constrainedSince = constrainedSinceMs.get(publication.key);
            sample(output, "aeron_ipc_publisher_position_bytes", labels, publication.position);
            sample(output, "aeron_ipc_publisher_rate_bytes_per_second", labels,
                sampleRates.getOrDefault(publication.positionCounterId, 0.0));
            sample(output, "aeron_ipc_publisher_window_bytes", labels, window);
            sample(output, "aeron_ipc_publisher_max_subscriber_lag_bytes", labels, maxLag);
            sample(output, "aeron_ipc_publisher_constrained", labels, constrained ? 1 : 0);
            sample(output, "aeron_ipc_publisher_constrained_duration_seconds", labels,
                constrained && null != constrainedSince ? (nowMs - constrainedSince) / 1_000.0 : 0);
        }

        metricHeader(output, "aeron_ipc_subscriber_position_bytes", "Current IPC subscriber position in bytes.");
        metricHeader(output, "aeron_ipc_subscriber_rate_bytes_per_second", "IPC subscriber rate over the sample interval.");
        metricHeader(output, "aeron_ipc_subscriber_lag_bytes", "IPC subscriber lag behind its publisher in bytes.");
        metricHeader(output, "aeron_ipc_subscriber_lagging", "Whether the IPC subscriber is behind its publisher.");
        for (final Subscriber subscriber : subscribers) {
            final String labels = subscriberLabels(
                displayName(applicationNames, subscriber.ownerId), subscriber);
            final Publication publication = publicationsByStream.get(subscriber.key);
            final long lag = null == publication || publication.positionCounterId < 0 ? 0 :
                Math.max(0, publication.position - subscriber.position);
            sample(output, "aeron_ipc_subscriber_position_bytes", labels, subscriber.position);
            sample(output, "aeron_ipc_subscriber_rate_bytes_per_second", labels,
                sampleRates.getOrDefault(subscriber.counterId, 0.0));
            sample(output, "aeron_ipc_subscriber_lag_bytes", labels, lag);
            sample(output, "aeron_ipc_subscriber_lagging", labels, lag > 0 ? 1 : 0);
        }

        driverMetric(output, "aeron_driver_errors", "Driver errors observed.",
            AeronCounters.SYSTEM_COUNTER_ID_ERRORS, systemCounters);
        driverMetric(output, "aeron_driver_unblocked_publications", "Publications unblocked by the driver.",
            AeronCounters.SYSTEM_COUNTER_ID_UNBLOCKED_PUBLICATIONS, systemCounters);
        driverMetric(output, "aeron_driver_client_timeouts", "Aeron client liveness timeouts.",
            AeronCounters.SYSTEM_COUNTER_ID_CLIENT_TIMEOUTS, systemCounters);
        driverMetric(output, "aeron_driver_publications_revoked", "Publications revoked by the driver.",
            AeronCounters.SYSTEM_COUNTER_ID_PUBLICATIONS_REVOKED, systemCounters);
        driverMetric(output, "aeron_driver_conductor_threshold_exceeded",
            "Conductor work cycles exceeding their threshold.",
            AeronCounters.SYSTEM_COUNTER_ID_CONDUCTOR_CYCLE_TIME_THRESHOLD_EXCEEDED, systemCounters);
        metricHeader(output, "aeron_driver_conductor_max_cycle_seconds", "Maximum conductor work cycle in seconds.");
        final CounterValue maxCycle = systemCounters.get(AeronCounters.SYSTEM_COUNTER_ID_CONDUCTOR_MAX_CYCLE_TIME);
        output.append("aeron_driver_conductor_max_cycle_seconds ")
            .append(null == maxCycle ? 0 : maxCycle.value / 1_000_000_000.0).append('\n');
        return output.toString();
    }

    private static void driverMetric(
        final StringBuilder output,
        final String name,
        final String help,
        final int systemCounterId,
        final Map<Integer, CounterValue> systemCounters) {
        metricHeader(output, name, help);
        final CounterValue value = systemCounters.get(systemCounterId);
        output.append(name).append(' ').append(null == value ? 0 : value.value).append('\n');
    }

    private static void metricHeader(final StringBuilder output, final String name, final String help) {
        output.append("# HELP ").append(name).append(' ').append(help).append('\n');
        output.append("# TYPE ").append(name).append(" gauge\n");
    }

    private static void sample(
        final StringBuilder output, final String name, final String labels, final double value) {
        output.append(name).append('{').append(labels).append("} ").append(value).append('\n');
    }

    private static String streamLabels(final String applicationName, final StreamKey key) {
        return "application=\"" + escapeLabel(applicationName) + "\",stream_id=\"" + key.streamId +
            "\",session_id=\"" + key.sessionId + "\"";
    }

    private static String subscriberLabels(final String applicationName, final Subscriber subscriber) {
        return streamLabels(applicationName, subscriber.key) + ",registration_id=\"" +
            subscriber.registrationId + "\"";
    }

    private static String escapeLabel(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private void appendHealthCounter(
        final StringBuilder view,
        final String name,
        final int systemCounterId,
        final Map<Integer, CounterValue> systemCounters,
        final long elapsedMs) {
        final CounterValue counter = systemCounters.get(systemCounterId);
        if (null != counter) {
            final long delta = delta(counter.counterId, counter.value, elapsedMs);
            view.append(name).append('=').append(counter.value);
            if (elapsedMs > 0 && delta > 0) {
                view.append("(+").append(delta).append(')');
            }
            view.append(' ');
        }
    }

    private double rate(final int counterId, final long value, final long elapsedMs) {
        if (elapsedMs <= 0) {
            remember(counterId, value);
            return Double.NaN;
        }
        return Math.max(0, delta(counterId, value, elapsedMs)) * 1_000.0 / elapsedMs;
    }

    private long delta(final int counterId, final long value, final long elapsedMs) {
        final PreviousValue previous = previousValues.put(counterId, new PreviousValue(value));
        return null == previous || elapsedMs <= 0 ? 0 : Math.max(0, value - previous.value);
    }

    private void remember(final int counterId, final long value) {
        previousValues.put(counterId, new PreviousValue(value));
    }

    private static boolean isIpcStreamPosition(final int typeId) {
        return AeronCounters.DRIVER_PUBLISHER_POS_TYPE_ID == typeId ||
            AeronCounters.DRIVER_PUBLISHER_LIMIT_TYPE_ID == typeId ||
            AeronCounters.DRIVER_SUBSCRIBER_POSITION_TYPE_ID == typeId;
    }

    private String applicationName(final int counterId) {
        final String label = counters.getCounterLabel(counterId);
        return label.startsWith(ApplicationCounters.APPLICATION_IDENTITY_LABEL_PREFIX) ?
            label.substring(ApplicationCounters.APPLICATION_IDENTITY_LABEL_PREFIX.length()) : label;
    }

    private static String displayName(final Map<Long, String> applicationNames, final long ownerId) {
        return applicationNames.getOrDefault(ownerId, "client-" + ownerId);
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

    private static String formatRate(final double bytesPerSecond) {
        return Double.isNaN(bytesPerSecond) ? "n/a" : formatBytes(bytesPerSecond) + "/s";
    }

    private static String formatBytes(final long bytes) {
        return formatBytes((double)bytes);
    }

    private static String formatBytes(final double bytes) {
        final double absolute = Math.abs(bytes);
        if (absolute >= 1_073_741_824) {
            return String.format(Locale.ROOT, "%.2f GiB", bytes / 1_073_741_824.0);
        }
        if (absolute >= 1_048_576) {
            return String.format(Locale.ROOT, "%.2f MiB", bytes / 1_048_576.0);
        }
        if (absolute >= 1_024) {
            return String.format(Locale.ROOT, "%.2f KiB", bytes / 1_024.0);
        }
        return String.format(Locale.ROOT, "%.0f B", bytes);
    }

    private static String formatDuration(final long durationMs) {
        return String.format(Locale.ROOT, "%.1fs", durationMs / 1_000.0);
    }

    private static String formatDurationNanos(final long durationNs) {
        if (durationNs >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.2fs", durationNs / 1_000_000_000.0);
        }
        if (durationNs >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.2fms", durationNs / 1_000_000.0);
        }
        if (durationNs >= 1_000L) {
            return String.format(Locale.ROOT, "%.2fus", durationNs / 1_000.0);
        }
        return durationNs + "ns";
    }

    private record StreamKey(int sessionId, int streamId, String channel) { }
    private record StreamMetadata(long registrationId, int sessionId, int streamId, String channel) { }
    private record Subscriber(int counterId, long ownerId, long registrationId, StreamKey key, long position) { }
    private record CounterValue(int counterId, long value) { }
    private record PreviousValue(long value) { }

    private static final class Publication {
        private final StreamKey key;
        private int positionCounterId = -1;
        private long ownerId;
        private long registrationId;
        private long position;
        private long limit;
        private boolean hasLimit;

        private Publication(final StreamKey key) {
            this.key = key;
        }
    }
}
