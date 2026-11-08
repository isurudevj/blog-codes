package io.github.aeron_experiments.pricingengine;

import io.aeron.Publication;
import io.github.aeron_experiments.common.AgentConfig;
import io.github.aeron_experiments.common.EnvInitializer;
import io.github.aeron_experiments.common.ThreadFactoryUtils;
import io.github.aeron_experiments.aeron_utils.AeronFactory;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ShutdownSignalBarrier;

@Slf4j
public final class PricingEngineApplication {

    private PricingEngineApplication() {
    }

    public static void main(final String[] args) {
        EnvInitializer.init();
        final String channel = Config.pricingChannel();
        final int streamId = Config.pricingStreamId();
        final String applicationName = Config.applicationName();
        final int messagesPerSecond = Config.messagesPerSecond();

        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
            AeronFactory aeronFactory = AeronFactory.initialize(applicationName)) {
            final Publication publication = aeronFactory.createPublication(channel, streamId);
            try (AgentRunner runner = new AgentRunner(
                AgentConfig.idleStrategy(),
                throwable -> log.error("Pricing Engine agent failed", throwable),
                null,
                new PricingEngineAgent(publication, messagesPerSecond))) {
                AgentRunner.startOnThread(
                    runner, ThreadFactoryUtils.createDemonThreadFactory(applicationName + "-agent"));
                log.info("Pricing Engine started: appName={}, channel={}, streamId={}, messagesPerSecond={}",
                    applicationName, channel, streamId, messagesPerSecond);
                barrier.await();
                log.info("Shutting down Pricing Engine");
            }
        }
    }
}
