package io.github.aeron_experiments.aeronarchive;

import io.aeron.archive.Archive;
import io.github.aeron_experiments.common.EnvInitializer;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AeronArchiveApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(AeronArchiveApplication.class);
    private static final String PRINT_CONFIGURATION_PROPERTY = "aeron.archive.print.configuration";

    private AeronArchiveApplication() {
    }

    public static void main(final String[] args) {
        EnvInitializer.init();
        final Archive.Context context = new Archive.Context();
        if (Boolean.getBoolean(PRINT_CONFIGURATION_PROPERTY)) {
            LOGGER.info("Aeron Archive configuration: {}", context);
        }

        try (Archive archive = Archive.launch(context);
             ShutdownSignalBarrier barrier = new ShutdownSignalBarrier()) {
            LOGGER.info("Aeron Archive started: aeronDir={}, archiveDir={}",
                context.aeronDirectoryName(), context.archiveDir().getAbsolutePath());
            barrier.await();
        }
    }
}
