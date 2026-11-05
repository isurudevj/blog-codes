package io.github.aeron_experiments.pricedistributor;

import io.aeron.Aeron;
import io.github.aeron_experiments.common.EnvInitializer;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.real_logic.artio.library.FixLibrary;
import uk.co.real_logic.artio.library.LibraryConfiguration;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public final class PriceDistributorApplication {

    public static void main(final String[] args) {
        EnvInitializer.init();
        final PriceDistributorSessionHandler handler = new PriceDistributorSessionHandler();
        final LibraryConfiguration configuration = new LibraryConfiguration()
            .libraryName("price-distributor")
            .libraryAeronChannels(List.of(Aeron.Context.IPC_CHANNEL))
            .sessionAcquireHandler((session, acquiredInfo) -> handler);
    }
}
