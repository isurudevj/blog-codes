package io.github.aeron_experiments.pricedistributor;

import io.aeron.Subscription;
import io.github.aeron_experiments.aeron_utils.AeronFactory;
import io.github.aeron_experiments.common.AgentConfig;
import io.github.aeron_experiments.common.EnvInitializer;
import io.github.aeron_experiments.common.ShutdownUtils;
import io.github.aeron_experiments.common.ThreadFactoryUtils;
import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ShutdownSignalBarrier;

@Slf4j
public final class PriceDistributorApplication {

    public static void main(final String[] args) {
        EnvInitializer.init();
        final String applicationName = Config.applicationName();
        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
             AeronFactory aeronFactory = AeronFactory.initialize(applicationName)) {
            final Subscription subscription = aeronFactory.createSubscription(
                Config.pricingChannel(), Config.pricingStreamId());
            AgentRunner agentRunner = new AgentRunner(AgentConfig.idleStrategy(),
                    throwable -> log.error("Error occurred", throwable),
                    null,
                    new LibraryHandlerAgent(subscription));
            AgentRunner.startOnThread(agentRunner, ThreadFactoryUtils.createDemonThreadFactory());

            ShutdownUtils.registerShutdownHook(barrier::signal);
            log.info("Price distributor started: appName={}, pricingChannel={}, pricingStreamId={}",
                applicationName, Config.pricingChannel(), Config.pricingStreamId());
            barrier.await();
            CloseHelper.quietClose(agentRunner);
        }
    }
}
