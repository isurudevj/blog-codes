package io.github.aeron_experiments.mediadriver;

import io.aeron.driver.MediaDriver;
import io.github.aeron_experiments.common.EnvInitializer;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.agrona.SystemUtil.loadPropertiesFiles;

public final class MediaDriverApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaDriverApplication.class);

    private MediaDriverApplication() {
    }

    @SuppressWarnings("try")
    public static void main(final String[] args) {
        // Each argument can be a properties file or URL. Properties loaded here
        // are used by the Context constructor and can tune deployment-specific
        // settings such as the Aeron directory, MTU, socket buffers, and BDP.
        loadPropertiesFiles(args);
        EnvInitializer.init();

        final MediaDriver.Context context = new MediaDriver.Context();

        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
            MediaDriver driver = MediaDriver.launch(context.terminationHook(barrier::signalAll))) {
            LOGGER.info("High-performance Aeron Media Driver started in {}", driver.aeronDirectoryName());
            barrier.await();
            LOGGER.info("Shutting down Aeron Media Driver");
        }
    }
}
