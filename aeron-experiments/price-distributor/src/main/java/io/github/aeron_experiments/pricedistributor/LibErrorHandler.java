package io.github.aeron_experiments.pricedistributor;

import io.aeron.logbuffer.ControlledFragmentHandler;
import lombok.extern.slf4j.Slf4j;
import uk.co.real_logic.artio.library.GatewayErrorHandler;
import uk.co.real_logic.artio.messages.GatewayError;

@Slf4j
public class LibErrorHandler implements GatewayErrorHandler {

    @Override
    public ControlledFragmentHandler.Action onError(GatewayError errorType, int libraryId, String message) {
        log.error("Lib id {} Error occurred {} | {}", libraryId, errorType, message);
        return ControlledFragmentHandler.Action.CONTINUE;
    }

}
