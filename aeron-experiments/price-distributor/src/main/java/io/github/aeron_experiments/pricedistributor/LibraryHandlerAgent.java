package io.github.aeron_experiments.pricedistributor;

import io.aeron.Aeron;
import io.github.aeron_experiments.common.ThreadFactoryUtils;
import org.agrona.CloseHelper;
import org.agrona.concurrent.Agent;
import uk.co.real_logic.artio.library.FixLibrary;
import uk.co.real_logic.artio.library.LibraryConfiguration;

import java.util.List;

public class LibraryHandlerAgent implements Agent {

    private FixLibrary fixLibrary;

    @Override
    public void onStart() {
        PriceDistributorSessionHandler handler = new PriceDistributorSessionHandler();
        LibraryConfiguration configuration = new LibraryConfiguration();
        configuration.sessionAcquireHandler(handler);
        configuration.sessionExistsHandler(handler);
        configuration.libraryConnectHandler(handler);
        configuration.gatewayErrorHandler(new LibErrorHandler());
        configuration.threadFactory(ThreadFactoryUtils.createUnnamedDemonThreadFactory(true));
        configuration.libraryAeronChannels(List.of(Aeron.Context.IPC_CHANNEL));


        fixLibrary = FixLibrary.connect(configuration);
    }

    @Override
    public int doWork() throws Exception {
        if (fixLibrary.isConnected()) {
            fixLibrary.poll(10);
        }
        return 0;
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
