package io.github.aeron_experiments.pricingengine;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.github.aeron_experiments.common.EnvInitializer;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PricingEngineApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(PricingEngineApplication.class);
    private static final String DEFAULT_CHANNEL = "aeron:udp?endpoint=127.0.0.1:40123";
    private static final int DEFAULT_STREAM_ID = 1001;

    private PricingEngineApplication() {
    }

    public static void main(final String[] args) {
        EnvInitializer.init();
        final String channel = System.getenv().getOrDefault("PRICING_CHANNEL", DEFAULT_CHANNEL);
        final int streamId = Integer.parseInt(
            System.getenv().getOrDefault("PRICING_STREAM_ID", Integer.toString(DEFAULT_STREAM_ID)));

        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
            Aeron aeron = Aeron.connect();
            Publication publication = aeron.addPublication(channel, streamId);
            AgentRunner runner = new AgentRunner(
                new BusySpinIdleStrategy(),
                throwable -> LOGGER.error("Pricing Engine agent failed", throwable),
                null,
                new PricingEngineAgent(publication))) {
            AgentRunner.startOnThread(runner);
            LOGGER.info("Pricing Engine started: channel={}, streamId={}", channel, streamId);
            barrier.await();
            LOGGER.info("Shutting down Pricing Engine");
        }
    }
}
