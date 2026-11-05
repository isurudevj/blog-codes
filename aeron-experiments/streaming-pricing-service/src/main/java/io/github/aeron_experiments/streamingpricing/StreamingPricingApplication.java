package io.github.aeron_experiments.streamingpricing;

import io.aeron.Aeron;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.real_logic.artio.library.FixLibrary;
import uk.co.real_logic.artio.library.LibraryConfiguration;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StreamingPricingApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingPricingApplication.class);

    private StreamingPricingApplication() {
    }

    public static void main(final String[] args) {
        final String aeronDirectory = env("AERON_DIR", Aeron.Context.getAeronDirectoryName());
        final String libraryChannel = env("ARTIO_LIBRARY_CHANNEL", "aeron:ipc");
        final StreamingPricingSessionHandler handler = new StreamingPricingSessionHandler();
        final LibraryConfiguration configuration = new LibraryConfiguration()
            .libraryName("streaming-pricing-service")
            .libraryAeronChannels(List.of(libraryChannel))
            .sessionAcquireHandler((session, acquiredInfo) -> handler);
        configuration.aeronContext().aeronDirectoryName(aeronDirectory);

        final AtomicBoolean running = new AtomicBoolean(true);
        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> running.set(false), "streaming-pricing-shutdown"));
        try (FixLibrary library = FixLibrary.connect(configuration)) {
            LOGGER.info("Streaming pricing service started: aeronDir={}, libraryChannel={}",
                aeronDirectory, libraryChannel);
            final BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy();
            while (running.get()) {
                idleStrategy.idle(library.poll(10));
            }
        }
    }

    private static String env(final String name, final String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }
}
