package io.github.aeron_experiments.monitoring.pojo;

import java.util.List;

public final class StreamInfo {
    private final String channel;
    private final int streamId;
    private final int sessionId;
    private final PublisherInfo publisher;
    private final List<SubscriberInfo> subscribers;

    public StreamInfo(
        final String channel,
        final int streamId,
        final int sessionId,
        final PublisherInfo publisher,
        final List<SubscriberInfo> subscribers) {
        this.channel = channel;
        this.streamId = streamId;
        this.sessionId = sessionId;
        this.publisher = publisher;
        this.subscribers = List.copyOf(subscribers);
    }

    public String getChannel() {
        return channel;
    }

    public int getStreamId() {
        return streamId;
    }

    public int getSessionId() {
        return sessionId;
    }

    public PublisherInfo getPublisher() {
        return publisher;
    }

    public List<SubscriberInfo> getSubscribers() {
        return subscribers;
    }
}
