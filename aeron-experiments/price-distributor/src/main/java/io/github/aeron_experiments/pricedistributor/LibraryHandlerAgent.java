package io.github.aeron_experiments.pricedistributor;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.github.aeron_experiments.common.ThreadFactoryUtils;
import org.agrona.CloseHelper;
import org.agrona.concurrent.Agent;
import uk.co.real_logic.artio.library.FixLibrary;
import uk.co.real_logic.artio.library.LibraryConfiguration;

import java.util.List;

public class LibraryHandlerAgent implements Agent {

    private FixLibrary fixLibrary;
    private final Subscription pricingSubscription;
    private PriceDistributorSessionHandler handler;

    LibraryHandlerAgent(final Subscription pricingSubscription) {
        this.pricingSubscription = pricingSubscription;
    }

    @Override
    public void onStart() {
        handler = new PriceDistributorSessionHandler(pricingSubscription);
        LibraryConfiguration configuration = new LibraryConfiguration();
        configuration.sessionAcquireHandler(handler);
        configuration.sessionExistsHandler(handler);
        configuration.libraryConnectHandler(handler);
        configuration.gatewayErrorHandler(new LibErrorHandler());
        configuration.threadFactory(ThreadFactoryUtils.createDemonThreadFactory());
        configuration.libraryAeronChannels(List.of(Aeron.Context.IPC_CHANNEL));


        fixLibrary = FixLibrary.connect(configuration);
    }

    @Override
    public int doWork() throws Exception {
        return fixLibrary.poll(10) + handler.pollPricing();
    }

    @Override
    public void onClose() {
        CloseHelper.quietClose(fixLibrary);
    }

    @Override
    public String roleName() {
        return "lib-agent";
    }
}
