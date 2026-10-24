package io.github.aeron_experiments.monitoring.pojo;

public final class PublisherInfo {
    private final String application;
    private final long registrationId;
    private final long ownerId;
    private final long positionBytes;
    private final double rateBytesPerSecond;

    public PublisherInfo(
        final String application,
        final long registrationId,
        final long ownerId,
        final long positionBytes,
        final double rateBytesPerSecond) {
        this.application = application;
        this.registrationId = registrationId;
        this.ownerId = ownerId;
        this.positionBytes = positionBytes;
        this.rateBytesPerSecond = rateBytesPerSecond;
    }

    public String getApplication() {
        return application;
    }

    public long getRegistrationId() {
        return registrationId;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public long getPositionBytes() {
        return positionBytes;
    }

    public double getRateBytesPerSecond() {
        return rateBytesPerSecond;
    }
}
