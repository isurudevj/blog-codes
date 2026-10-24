package io.github.aeron_experiments.monitoring;

import io.aeron.Aeron;
import io.github.aeron_experiments.common.EnvInitializer;
import io.github.aeron_experiments.common.ShutdownUtils;
import io.github.aeron_experiments.common.ThreadFactoryUtils;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingIdleStrategy;

import java.nio.file.Paths;

@Slf4j
public class MonitoringServiceApplication {

    public static void main(final String[] args) {
        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier()) {
            EnvInitializer.init();
            ShutdownUtils.registerShutdownHook(barrier::signal);

            AgentRunner agentRunner = new AgentRunner(new SleepingIdleStrategy(),
                    throwable -> log.error("Error occurred", throwable),
                    null,
                    new MonitoringAgent());
            AgentRunner.startOnThread(agentRunner,
                    ThreadFactoryUtils.createDemonThreadFactory("MonitoringService"));

            log.info("Monitoring service started");

            barrier.await();
            agentRunner.close();
        } catch (Exception e) {
            log.error("Error while starting up MonitoringServiceApplication", e);
        }
    }

}
