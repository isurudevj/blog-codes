package io.github.aeron_experiments.pricedistributor;

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
        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier()) {
            AgentRunner agentRunner = new AgentRunner(AgentConfig.idleStrategy(),
                    throwable -> log.error("Error occurred", throwable),
                    null,
                    new LibraryHandlerAgent());
            AgentRunner.startOnThread(agentRunner, ThreadFactoryUtils.createDemonThreadFactory());

            ShutdownUtils.registerShutdownHook(barrier::signal);
            log.info("Price distributor started");
            barrier.await();
            CloseHelper.quietClose(agentRunner);
        }
    }
}
