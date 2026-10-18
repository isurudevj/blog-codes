package io.github.aeron_experiments.monitoring;

import java.util.List;

record MonitoringStatistics(
    long timestampEpochMillis,
    List<PublicationStatistics> publications,
    List<SubscriberStatistics> subscribers,
    DriverStatistics driver) {

    record PublicationStatistics(
        String application,
        int streamId,
        int sessionId,
        long positionBytes,
        double rateBytesPerSecond,
        long availableWindowBytes,
        long maximumSubscriberLagBytes,
        boolean constrained,
        double constrainedDurationSeconds) { }

    record SubscriberStatistics(
        String application,
        int streamId,
        int sessionId,
        long registrationId,
        long positionBytes,
        double rateBytesPerSecond,
        long lagBytes,
        boolean lagging) { }

    record DriverStatistics(
        long errors,
        long unblockedPublications,
        long clientTimeouts,
        long publicationsRevoked,
        long conductorThresholdExceeded,
        long conductorMaximumCycleNs) { }
}
