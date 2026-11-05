package io.github.aeron_experiments.fixengine;

import io.github.aeron_experiments.artio_support.CustomEngineConfiguration;
import io.github.aeron_experiments.common.EnvInitializer;
import io.github.aeron_experiments.common.ShutdownUtils;
import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.concurrent.ShutdownSignalBarrier;
import uk.co.real_logic.artio.engine.FixEngine;

@Slf4j
public final class ArtioFixEngineApplication {

    public static void main(final String[] args) {
        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier()) {
            EnvInitializer.init();
            final CustomEngineConfiguration configuration = new CustomEngineConfiguration();

            FixEngine engine = FixEngine.launch(configuration.engineConfiguration());
            log.info("Artio FIX engine started: bind={}:{}, aeronDir={}, archiveLogging=true, logDir={}",
                    configuration.bindToHost(), configuration.bindToPort(),
                    configuration.aeronDirectoryName(), configuration.logFileDir());
            log.info("Continuous archive pruning enabled={}", configuration.archivePruningEnabled());

            ShutdownUtils.registerShutdownHook(barrier::signal);

            barrier.await();
            CloseHelper.quietClose(engine);
        }
    }
}
