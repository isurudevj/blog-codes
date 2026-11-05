package io.github.aeron_experiments.fixengine;

import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.concurrent.ShutdownSignalBarrier;
import uk.co.real_logic.artio.engine.FixEngine;

@Slf4j
public final class ArtioFixEngineApplication {

    public static void main(final String[] args) {
        final CustomEngineConfiguration configuration = new CustomEngineConfiguration();

        try (FixEngine engine = FixEngine.launch(configuration.engineConfiguration());
             ShutdownSignalBarrier barrier = new ShutdownSignalBarrier()) {
            log.info("Artio FIX engine started: bind={}:{}, aeronDir={}, archiveLogging=true, logDir={}",
                configuration.bindToHost(), configuration.bindToPort(),
                configuration.aeronDirectoryName(), configuration.logFileDir());
            log.info("Continuous archive pruning enabled={}", configuration.archivePruningEnabled());


            ShutdownU
            barrier.await();

            CloseHelper.quietClose(engine);
        }
    }
}
