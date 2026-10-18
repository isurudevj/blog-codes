package io.github.aeron_experiments.monitoring;

import io.github.aeron_experiments.common.EnvInitializer;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
@Slf4j
public class MonitoringServiceApplication {

    public static void main(final String[] args) {
        EnvInitializer.init();
        SpringApplication.run(MonitoringServiceApplication.class, args);
    }

    @Bean
    BlockingQueue<MonitoringStatistics> statisticsQueue() {
        return new ArrayBlockingQueue<>(Config.statisticsQueueCapacity());
    }

    @Bean
    AtomicLong droppedStatisticsCounter() {
        return new AtomicLong();
    }

    @Bean(destroyMethod = "close")
    CncCounters cncCounters() {
        return CncCounters.open(Config.aeronDirectory());
    }

    @Bean(destroyMethod = "close")
    AgentRunner monitoringAgentRunner(
        final CncCounters cncCounters,
        final BlockingQueue<MonitoringStatistics> statisticsQueue,
        final AtomicLong droppedStatisticsCounter) {
        return new AgentRunner(
            new SleepingMillisIdleStrategy(100),
            throwable -> log.error("Monitoring agent failed", throwable),
            null,
            new IpcMonitoringAgent(
                cncCounters.countersReader(),
                Config.intervalMs(),
                statisticsQueue,
                droppedStatisticsCounter));
    }

    @Bean
    CommandLineRunner startMonitoringAgent(final AgentRunner monitoringAgentRunner) {
        return args -> AgentRunner.startOnThread(monitoringAgentRunner);
    }
}
