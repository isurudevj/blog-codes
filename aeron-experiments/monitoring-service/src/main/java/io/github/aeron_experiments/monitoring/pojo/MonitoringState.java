package io.github.aeron_experiments.monitoring.pojo;

import java.util.List;

public final class MonitoringState {
    private final String observedAt;
    private final MediaDriverState mediaDriver;
    private final List<StreamInfo> streams;

    public MonitoringState(
        final String observedAt,
        final MediaDriverState mediaDriver,
        final List<StreamInfo> streams) {
        this.observedAt = observedAt;
        this.mediaDriver = mediaDriver;
        this.streams = List.copyOf(streams);
    }

    public String getObservedAt() {
        return observedAt;
    }

    public MediaDriverState getMediaDriver() {
        return mediaDriver;
    }

    public List<StreamInfo> getStreams() {
        return streams;
    }
}
