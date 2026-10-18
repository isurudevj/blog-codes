package io.github.aeron_experiments.pricingclient;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.github.aeron_experiments.common.EnvInitializer;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

@Slf4j
public final class PricingClientApplication {

    private PricingClientApplication() {
    }

    public static void main(final String[] args) {
        EnvInitializer.init();
        final String channel = Config.pricingChannel();
        final int streamId = Config.pricingStreamId();

        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
            Aeron aeron = Aeron.connect();
            Subscription subscription = aeron.addSubscription(channel, streamId);
            AgentRunner runner = new AgentRunner(
                new BusySpinIdleStrategy(),
                throwable -> log.error("Pricing Client agent failed", throwable),
                null,
                new PricingClientAgent(subscription))) {
            AgentRunner.startOnThread(runner);
            log.info("Pricing Client started: channel={}, streamId={}", channel, streamId);
            barrier.await();
            log.info("Shutting down Pricing Client");
        }
    }
}
