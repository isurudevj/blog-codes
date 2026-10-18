package io.github.aeron_experiments.pricingclient;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.github.aeron_experiments.common.EnvInitializer;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PricingClientApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(PricingClientApplication.class);
    private static final String DEFAULT_CHANNEL = "aeron:udp?endpoint=127.0.0.1:40123";
    private static final int DEFAULT_STREAM_ID = 1001;

    private PricingClientApplication() {
    }

    public static void main(final String[] args) {
        EnvInitializer.init();
        final String channel = System.getenv().getOrDefault("PRICING_CHANNEL", DEFAULT_CHANNEL);
        final int streamId = Integer.parseInt(
            System.getenv().getOrDefault("PRICING_STREAM_ID", Integer.toString(DEFAULT_STREAM_ID)));

        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
            Aeron aeron = Aeron.connect();
            Subscription subscription = aeron.addSubscription(channel, streamId);
            AgentRunner runner = new AgentRunner(
                new BusySpinIdleStrategy(),
                throwable -> LOGGER.error("Pricing Client agent failed", throwable),
                null,
                new PricingClientAgent(subscription))) {
            AgentRunner.startOnThread(runner);
            LOGGER.info("Pricing Client started: channel={}, streamId={}", channel, streamId);
            barrier.await();
            LOGGER.info("Shutting down Pricing Client");
        }
    }
}
