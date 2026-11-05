package io.github.aeron_experiments.aeronarchive;

import io.aeron.archive.Archive;
import io.github.aeron_experiments.common.EnvInitializer;
import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.concurrent.ShutdownSignalBarrier;

@Slf4j
public final class AeronArchiveApplication {

    public static void main(final String[] args) {
        EnvInitializer.init();

        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier()) {
            final Archive.Context context = new Archive.Context();
            Archive archive = Archive.launch(context);
            log.info("AeronArchive started \n{}", context);
            barrier.await();

            CloseHelper.quietClose(archive);
        }
    }
}
