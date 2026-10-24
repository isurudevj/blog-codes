package io.github.aeron_experiments.pricingclient;

import io.aeron.Subscription;
import io.github.aeron_experiments.common.AgentConfig;
import io.github.aeron_experiments.common.EnvInitializer;
import io.github.aeron_experiments.common.ThreadFactoryUtils;
import io.github.aeron_experiments.aeron_utils.AeronFactory;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ShutdownSignalBarrier;

@Slf4j
public final class PricingClientApplication {

    private PricingClientApplication() {
    }

    public static void main(final String[] args) {
        EnvInitializer.init();
        final String channel = Config.pricingChannel();
        final int streamId = Config.pricingStreamId();
        final String applicationName = Config.applicationName();

        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
            AeronFactory aeronFactory = AeronFactory.initialize(applicationName)) {
            final Subscription subscription = aeronFactory.createSubscription(channel, streamId);
            try (AgentRunner runner = new AgentRunner(
                AgentConfig.idleStrategy(),
                throwable -> log.error("Pricing Client agent failed", throwable),
                null,
                new PricingClientAgent(subscription))) {
                AgentRunner.startOnThread(
                    runner, ThreadFactoryUtils.createDemonThreadFactory(applicationName + "-agent"));
                log.info("Pricing Client started: appName={}, channel={}, streamId={}",
                    applicationName, channel, streamId);
                barrier.await();
                log.info("Shutting down Pricing Client");
            }
        }
    }
}
