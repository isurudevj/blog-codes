package io.github.aeron_experiments.pricedistributor;

import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import io.github.aeron_experiments.artio_codecs.builder.MarketDataSnapshotFullRefreshEncoder;
import io.github.aeron_experiments.artio_codecs.decoder.MarketDataRequestDecoder;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import uk.co.real_logic.artio.CommonConfiguration;
import uk.co.real_logic.artio.Reply;
import uk.co.real_logic.artio.library.FixLibrary;
import uk.co.real_logic.artio.library.LibraryConnectHandler;
import uk.co.real_logic.artio.library.OnMessageInfo;
import uk.co.real_logic.artio.library.SessionAcquireHandler;
import uk.co.real_logic.artio.library.SessionAcquiredInfo;
import uk.co.real_logic.artio.library.SessionExistsHandler;
import uk.co.real_logic.artio.library.SessionHandler;
import uk.co.real_logic.artio.messages.DisconnectReason;
import uk.co.real_logic.artio.messages.SessionReplyStatus;
import uk.co.real_logic.artio.session.Session;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

@Slf4j
final class PriceDistributorSessionHandler implements SessionHandler, SessionExistsHandler, SessionAcquireHandler, LibraryConnectHandler {

    private static final long BID_PRICE = 18_950L;
    private static final long OFFER_PRICE = 18_955L;
    private static final int PRICE_SCALE = 2;
    private final MarketDataRequestDecoder request = new MarketDataRequestDecoder();
    private final MarketDataSnapshotFullRefreshEncoder snapshot = new MarketDataSnapshotFullRefreshEncoder();
    private final MutableAsciiBuffer asciiBuffer = new MutableAsciiBuffer();

    @Override
    public Action onMessage(
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final int libraryId,
            final Session session,
            final int sequenceIndex,
            final long messageType,
            final long timestampInNs,
            final long position,
            final OnMessageInfo messageInfo) {

        return Action.CONTINUE;
    }

    @Override
    public void onTimeout(final int libraryId, final Session session) {
        log.warn("FIX session timed out: {}", session.compositeKey());
    }

    @Override
    public void onSlowStatus(final int libraryId, final Session session, final boolean hasBecomeSlow) {
        log.warn("FIX session slow status changed: session={}, slow={}", session.compositeKey(), hasBecomeSlow);
    }

    @Override
    public Action onDisconnect(final int libraryId, final Session session, final DisconnectReason reason) {
        log.info("FIX session disconnected: session={}, reason={}", session.compositeKey(), reason);
        return Action.CONTINUE;
    }

    @Override
    public void onSessionStart(final Session session) {
        log.info("FIX session active: {}", session.compositeKey());
    }

    @Override
    public SessionHandler onSessionAcquired(Session session, SessionAcquiredInfo acquiredInfo) {
        log.info("Session acquired {} -> {}", session.compositeKey().localCompId(),
                session.compositeKey().remoteCompId());
        return this;
    }

    @Override
    public void onSessionExists(FixLibrary library, long surrogateSessionId, String localCompId, String localSubId,
                                String localLocationId, String remoteCompId, String remoteSubId, String remoteLocationId,
                                int logonReceivedSequenceNumber, int logonSequenceIndex) {
        log.info("New session found {} -> {}", localCompId, remoteCompId);
        final Reply<SessionReplyStatus> reply = library.requestSession(
                surrogateSessionId,
                logonReceivedSequenceNumber,
                logonSequenceIndex,
                CommonConfiguration.DEFAULT_REPLY_TIMEOUT_IN_MS);
    }

    @Override
    public void onConnect(FixLibrary library) {
        log.info("Connected to fix engine");
    }

    @Override
    public void onDisconnect(FixLibrary library) {
        log.info("Disconnected from fix engine");
    }
}
