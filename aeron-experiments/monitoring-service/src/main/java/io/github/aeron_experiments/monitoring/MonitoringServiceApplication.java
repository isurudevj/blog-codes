package io.github.aeron_experiments.monitoring;

import io.github.aeron_experiments.common.EnvInitializer;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

@Slf4j
public final class MonitoringServiceApplication {
    private MonitoringServiceApplication() {
    }

    public static void main(final String[] args) throws Exception {
        EnvInitializer.init();

        try (CncCounters cncCounters = CncCounters.open(Config.aeronDirectory());
            PrometheusMetricsServer metricsServer = new PrometheusMetricsServer(
                Config.metricsHost(), Config.metricsPort());
            ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
            AgentRunner runner = new AgentRunner(
                new SleepingMillisIdleStrategy(100),
                throwable -> log.error("Monitoring agent failed", throwable),
                null,
                new IpcMonitoringAgent(cncCounters.countersReader(), Config.intervalMs(), metricsServer))) {
            metricsServer.start();
            AgentRunner.startOnThread(runner);
            log.info("Aeron IPC monitoring started: directory={}, interval={}s, metrics=http://{}:{}/metrics",
                Config.aeronDirectory(), Config.intervalSeconds(), Config.metricsHost(), Config.metricsPort());
            barrier.await();
            log.info("Shutting down Aeron IPC monitoring");
        }
    }
}
