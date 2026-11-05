package io.github.aeron_experiments.mediadriver;

import io.aeron.driver.MediaDriver;
import io.github.aeron_experiments.common.EnvInitializer;
import io.github.aeron_experiments.common.ShutdownUtils;
import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.concurrent.ShutdownSignalBarrier;

@Slf4j
public final class MediaDriverApplication {

    private MediaDriverApplication() {
    }

    @SuppressWarnings("try")
    public static void main(final String[] args) {
        EnvInitializer.init();

        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier()) {
            final MediaDriver.Context context = new MediaDriver.Context();
            MediaDriver mediaDriver = MediaDriver.launch(context);
            log.info("High-performance Aeron Media Driver started in {}", mediaDriver.aeronDirectoryName());
            ShutdownUtils.registerShutdownHook(barrier::signal);
            barrier.await();
            CloseHelper.quietClose(mediaDriver);
            log.info("Shutting down Aeron Media Driver");
        }
    }
}
