package io.github.aeron_experiments.pricingclient;

import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.github.aeron_experiments.codec_utils.OrderBookSnapshot;
import io.github.aeron_experiments.codec_utils.OrderBookSnapshotCodec;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PricingClientAgent implements Agent {
    private static final Logger LOGGER = LoggerFactory.getLogger(PricingClientAgent.class);
    private static final int BOOK_CAPACITY = 20;
    private static final int FRAGMENT_LIMIT = 10;
    private static final long REPORT_INTERVAL_NS = 1_000_000_000L;

    private final Subscription subscription;
    private final OrderBookSnapshot snapshot = new OrderBookSnapshot();
    private final OrderBookSnapshotCodec codec = new OrderBookSnapshotCodec();
    private final FragmentHandler fragmentHandler = this::onFragment;

    private long reportStartNs = System.nanoTime();
    private long intervalMessages;
    private long totalMessages;
    private long intervalLatencyNs;
    private long maxLatencyNs;

    PricingClientAgent(final Subscription subscription) {
        this.subscription = subscription;
        snapshot.setSymbol(new char[16]);
        snapshot.setBids(new long[BOOK_CAPACITY]);
        snapshot.setAsks(new long[BOOK_CAPACITY]);
        snapshot.setBidSizes(new long[BOOK_CAPACITY]);
        snapshot.setAskSizes(new long[BOOK_CAPACITY]);
    }

    public String roleName() {
        return "pricing-client";
    }

    public int doWork() {
        final int fragmentsRead = subscription.poll(fragmentHandler, FRAGMENT_LIMIT);
        reportIfDue();
        return fragmentsRead;
    }

    private void onFragment(
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final io.aeron.logbuffer.Header header) {
        codec.decode(buffer, offset, snapshot);
        final long latencyNs = System.nanoTime() - snapshot.getSentTimestamp();
        intervalMessages++;
        totalMessages++;
        intervalLatencyNs += latencyNs;
        if (latencyNs > maxLatencyNs) {
            maxLatencyNs = latencyNs;
        }
    }

    private void reportIfDue() {
        final long nowNs = System.nanoTime();
        final long elapsedNs = nowNs - reportStartNs;
        if (elapsedNs < REPORT_INTERVAL_NS) {
            return;
        }

        final long messagesPerSecond = intervalMessages * REPORT_INTERVAL_NS / elapsedNs;
        final long averageLatencyNs = intervalMessages == 0 ? 0 : intervalLatencyNs / intervalMessages;
        LOGGER.info(
            "rate={} msg/s, avgLatency={} ns, maxLatency={} ns, totalMessages={}",
            messagesPerSecond,
            averageLatencyNs,
            maxLatencyNs,
            totalMessages);

        reportStartNs = nowNs;
        intervalMessages = 0;
        intervalLatencyNs = 0;
        maxLatencyNs = 0;
    }
}
