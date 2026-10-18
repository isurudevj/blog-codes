package io.github.aeron_experiments.monitoring;

import io.github.aeron_experiments.common.ThreadFactoryUtils;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
final class StatisticsMetricsConsumer implements SmartLifecycle {
    private final BlockingQueue<MonitoringStatistics> queue;
    private final AtomicLong droppedStatistics;
    private final AtomicBoolean running = new AtomicBoolean();
    private final MultiGauge publisherPosition;
    private final MultiGauge publisherRate;
    private final MultiGauge publisherWindow;
    private final MultiGauge publisherMaximumLag;
    private final MultiGauge publisherConstrained;
    private final MultiGauge publisherConstrainedDuration;
    private final MultiGauge subscriberPosition;
    private final MultiGauge subscriberRate;
    private final MultiGauge subscriberLag;
    private final MultiGauge subscriberLagging;
    private final AtomicLong ready = new AtomicLong();
    private final AtomicLong snapshotTimestampMs = new AtomicLong();
    private final AtomicLong driverErrors = new AtomicLong();
    private final AtomicLong unblockedPublications = new AtomicLong();
    private final AtomicLong clientTimeouts = new AtomicLong();
    private final AtomicLong publicationsRevoked = new AtomicLong();
    private final AtomicLong conductorThresholdExceeded = new AtomicLong();
    private final AtomicLong conductorMaximumCycleNs = new AtomicLong();
    private Thread consumerThread;

    StatisticsMetricsConsumer(
        final BlockingQueue<MonitoringStatistics> queue,
        final AtomicLong droppedStatistics,
        final MeterRegistry registry) {
        this.queue = queue;
        this.droppedStatistics = droppedStatistics;
        publisherPosition = multiGauge(registry, "aeron.ipc.publisher.position.bytes");
        publisherRate = multiGauge(registry, "aeron.ipc.publisher.rate.bytes.per.second");
        publisherWindow = multiGauge(registry, "aeron.ipc.publisher.window.bytes");
        publisherMaximumLag = multiGauge(registry, "aeron.ipc.publisher.max.subscriber.lag.bytes");
        publisherConstrained = multiGauge(registry, "aeron.ipc.publisher.constrained");
        publisherConstrainedDuration = multiGauge(registry, "aeron.ipc.publisher.constrained.duration.seconds");
        subscriberPosition = multiGauge(registry, "aeron.ipc.subscriber.position.bytes");
        subscriberRate = multiGauge(registry, "aeron.ipc.subscriber.rate.bytes.per.second");
        subscriberLag = multiGauge(registry, "aeron.ipc.subscriber.lag.bytes");
        subscriberLagging = multiGauge(registry, "aeron.ipc.subscriber.lagging");

        Gauge.builder("aeron.monitor.ready", ready, AtomicLong::get).register(registry);
        Gauge.builder("aeron.monitor.snapshot.timestamp.seconds", snapshotTimestampMs,
            value -> value.get() / 1_000.0).register(registry);
        Gauge.builder("aeron.monitor.queue.dropped", droppedStatistics, AtomicLong::get).register(registry);
        Gauge.builder("aeron.driver.errors", driverErrors, AtomicLong::get).register(registry);
        Gauge.builder("aeron.driver.unblocked.publications", unblockedPublications, AtomicLong::get).register(registry);
        Gauge.builder("aeron.driver.client.timeouts", clientTimeouts, AtomicLong::get).register(registry);
        Gauge.builder("aeron.driver.publications.revoked", publicationsRevoked, AtomicLong::get).register(registry);
        Gauge.builder("aeron.driver.conductor.threshold.exceeded", conductorThresholdExceeded, AtomicLong::get)
            .register(registry);
        Gauge.builder("aeron.driver.conductor.max.cycle.seconds", conductorMaximumCycleNs,
            value -> value.get() / 1_000_000_000.0).register(registry);
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            consumerThread = ThreadFactoryUtils.createDemonThreadFactory("statistics-metrics")
                .newThread(this::consume);
            consumerThread.start();
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false) && null != consumerThread) {
            consumerThread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void consume() {
        while (running.get()) {
            try {
                update(queue.take());
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void update(final MonitoringStatistics statistics) {
        publisherPosition.register(publicationRows(statistics, PublicationValue.POSITION), true);
        publisherRate.register(publicationRows(statistics, PublicationValue.RATE), true);
        publisherWindow.register(publicationRows(statistics, PublicationValue.WINDOW), true);
        publisherMaximumLag.register(publicationRows(statistics, PublicationValue.MAXIMUM_LAG), true);
        publisherConstrained.register(publicationRows(statistics, PublicationValue.CONSTRAINED), true);
        publisherConstrainedDuration.register(
            publicationRows(statistics, PublicationValue.CONSTRAINED_DURATION), true);
        subscriberPosition.register(subscriberRows(statistics, SubscriberValue.POSITION), true);
        subscriberRate.register(subscriberRows(statistics, SubscriberValue.RATE), true);
        subscriberLag.register(subscriberRows(statistics, SubscriberValue.LAG), true);
        subscriberLagging.register(subscriberRows(statistics, SubscriberValue.LAGGING), true);

        final MonitoringStatistics.DriverStatistics driver = statistics.driver();
        driverErrors.set(driver.errors());
        unblockedPublications.set(driver.unblockedPublications());
        clientTimeouts.set(driver.clientTimeouts());
        publicationsRevoked.set(driver.publicationsRevoked());
        conductorThresholdExceeded.set(driver.conductorThresholdExceeded());
        conductorMaximumCycleNs.set(driver.conductorMaximumCycleNs());
        snapshotTimestampMs.set(statistics.timestampEpochMillis());
        ready.set(1);
    }

    private static List<MultiGauge.Row<Number>> publicationRows(
        final MonitoringStatistics statistics, final PublicationValue value) {
        return statistics.publications().stream()
            .map(publication -> MultiGauge.Row.of(publicationTags(publication), value.read(publication)))
            .toList();
    }

    private static List<MultiGauge.Row<Number>> subscriberRows(
        final MonitoringStatistics statistics, final SubscriberValue value) {
        return statistics.subscribers().stream()
            .map(subscriber -> MultiGauge.Row.of(subscriberTags(subscriber), value.read(subscriber)))
            .toList();
    }

    private static Tags publicationTags(final MonitoringStatistics.PublicationStatistics statistics) {
        return Tags.of(
            "application", statistics.application(),
            "stream_id", Integer.toString(statistics.streamId()),
            "session_id", Integer.toString(statistics.sessionId()));
    }

    private static Tags subscriberTags(final MonitoringStatistics.SubscriberStatistics statistics) {
        return Tags.of(
            "application", statistics.application(),
            "stream_id", Integer.toString(statistics.streamId()),
            "session_id", Integer.toString(statistics.sessionId()),
            "registration_id", Long.toString(statistics.registrationId()));
    }

    private static MultiGauge multiGauge(final MeterRegistry registry, final String name) {
        return MultiGauge.builder(name).register(registry);
    }

    private enum PublicationValue {
        POSITION { double read(final MonitoringStatistics.PublicationStatistics value) { return value.positionBytes(); } },
        RATE { double read(final MonitoringStatistics.PublicationStatistics value) { return value.rateBytesPerSecond(); } },
        WINDOW { double read(final MonitoringStatistics.PublicationStatistics value) { return value.availableWindowBytes(); } },
        MAXIMUM_LAG { double read(final MonitoringStatistics.PublicationStatistics value) { return value.maximumSubscriberLagBytes(); } },
        CONSTRAINED { double read(final MonitoringStatistics.PublicationStatistics value) { return value.constrained() ? 1 : 0; } },
        CONSTRAINED_DURATION { double read(final MonitoringStatistics.PublicationStatistics value) { return value.constrainedDurationSeconds(); } };

        abstract double read(MonitoringStatistics.PublicationStatistics value);
    }

    private enum SubscriberValue {
        POSITION { double read(final MonitoringStatistics.SubscriberStatistics value) { return value.positionBytes(); } },
        RATE { double read(final MonitoringStatistics.SubscriberStatistics value) { return value.rateBytesPerSecond(); } },
        LAG { double read(final MonitoringStatistics.SubscriberStatistics value) { return value.lagBytes(); } },
        LAGGING { double read(final MonitoringStatistics.SubscriberStatistics value) { return value.lagging() ? 1 : 0; } };

        abstract double read(MonitoringStatistics.SubscriberStatistics value);
    }
}
