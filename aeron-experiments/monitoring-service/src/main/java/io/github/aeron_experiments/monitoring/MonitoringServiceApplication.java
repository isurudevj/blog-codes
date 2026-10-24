package io.github.aeron_experiments.monitoring;

import io.aeron.Aeron;
import io.github.aeron_experiments.common.EnvInitializer;
import io.github.aeron_experiments.common.ShutdownUtils;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.ShutdownSignalBarrier;

import java.nio.file.Paths;

@Slf4j
public class MonitoringServiceApplication {

    public static void main(final String[] args) {
        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier()) {
            EnvInitializer.init();
            CncCounters counters = CncCounters.open(Paths.get(Aeron.Context.getAeronDirectoryName()).toFile());
            ShutdownUtils.registerShutdownHook(barrier::signal);



            barrier.await();
            counters.close();
        } catch (Exception e) {
            log.error("Error while starting up MonitoringServiceApplication", e);
        }
    }

}
