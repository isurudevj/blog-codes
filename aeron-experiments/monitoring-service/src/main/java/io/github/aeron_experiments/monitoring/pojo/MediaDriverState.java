package io.github.aeron_experiments.monitoring.pojo;

public final class MediaDriverState {
    private final boolean active;
    private final Long heartbeatAgeMs;

    public MediaDriverState(final boolean active, final Long heartbeatAgeMs) {
        this.active = active;
        this.heartbeatAgeMs = heartbeatAgeMs;
    }

    public boolean isActive() {
        return active;
    }

    public Long getHeartbeatAgeMs() {
        return heartbeatAgeMs;
    }
}
