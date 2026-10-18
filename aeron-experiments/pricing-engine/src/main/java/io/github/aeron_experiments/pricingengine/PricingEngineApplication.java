package io.github.aeron_experiments.pricingengine;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.github.aeron_experiments.common.EnvInitializer;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

@Slf4j
public final class PricingEngineApplication {

    private PricingEngineApplication() {
    }

    public static void main(final String[] args) {
        EnvInitializer.init();
        final String channel = Config.pricingChannel();
        final int streamId = Config.pricingStreamId();

        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
            Aeron aeron = Aeron.connect();
            Publication publication = aeron.addPublication(channel, streamId);
            AgentRunner runner = new AgentRunner(
                new BusySpinIdleStrategy(),
                throwable -> log.error("Pricing Engine agent failed", throwable),
                null,
                new PricingEngineAgent(publication))) {
            AgentRunner.startOnThread(runner);
            log.info("Pricing Engine started: channel={}, streamId={}", channel, streamId);
            barrier.await();
            log.info("Shutting down Pricing Engine");
        }
    }
}
