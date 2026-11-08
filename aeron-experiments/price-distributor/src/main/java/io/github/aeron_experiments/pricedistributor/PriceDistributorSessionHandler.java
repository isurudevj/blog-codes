package io.github.aeron_experiments.pricedistributor;

import io.aeron.Subscription;
import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import io.aeron.logbuffer.FragmentHandler;
import io.github.aeron_experiments.artio_codecs.MDEntryType;
import io.github.aeron_experiments.artio_codecs.builder.MarketDataSnapshotFullRefreshEncoder;
import io.github.aeron_experiments.artio_codecs.decoder.MarketDataRequestDecoder;
import io.github.aeron_experiments.codec_utils.OrderBookSnapshot;
import io.github.aeron_experiments.codec_utils.OrderBookSnapshotCodec;
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

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
final class PriceDistributorSessionHandler implements SessionHandler, SessionExistsHandler, SessionAcquireHandler, LibraryConnectHandler {

    private static final int BOOK_CAPACITY = 20;
    private static final int FRAGMENT_LIMIT = 10;
    private static final char DISABLE_PREVIOUS_SUBSCRIPTION = '2';
    private final Subscription pricingSubscription;
    private final MarketDataRequestDecoder request = new MarketDataRequestDecoder();
    private final MarketDataSnapshotFullRefreshEncoder snapshot = new MarketDataSnapshotFullRefreshEncoder();
    private final MutableAsciiBuffer asciiBuffer = new MutableAsciiBuffer();
    private final OrderBookSnapshot orderBook = new OrderBookSnapshot();
    private final OrderBookSnapshotCodec orderBookCodec = new OrderBookSnapshotCodec();
    private final FragmentHandler pricingHandler = this::onPricingFragment;
    private final Map<Session, ClientSubscription> clientSubscriptions = new IdentityHashMap<>();

    PriceDistributorSessionHandler(final Subscription pricingSubscription) {
        this.pricingSubscription = pricingSubscription;
        orderBook.setSymbol(new char[16]);
        orderBook.setBids(new long[BOOK_CAPACITY]);
        orderBook.setAsks(new long[BOOK_CAPACITY]);
        orderBook.setBidSizes(new long[BOOK_CAPACITY]);
        orderBook.setAskSizes(new long[BOOK_CAPACITY]);
    }

    int pollPricing() {
        return pricingSubscription.poll(pricingHandler, FRAGMENT_LIMIT);
    }

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

        if (messageType != MarketDataRequestDecoder.MESSAGE_TYPE) {
            return Action.CONTINUE;
        }

        asciiBuffer.wrap(buffer);
        request.decode(asciiBuffer, offset, length);
        if (request.subscriptionRequestType() == DISABLE_PREVIOUS_SUBSCRIPTION) {
            clientSubscriptions.remove(session);
            log.info("Removed market data subscription for session={}", session.compositeKey());
            return Action.CONTINUE;
        }

        final Iterator<MarketDataRequestDecoder.RelatedSymGroupDecoder> symbols =
            request.relatedSymGroupIterator().iterator();
        if (!symbols.hasNext()) {
            log.warn("Ignoring market data request without a symbol: session={}", session.compositeKey());
            return Action.CONTINUE;
        }

        final String symbol = symbols.next().symbolAsString();
        final String requestId = request.mDReqIDAsString();
        clientSubscriptions.put(session, new ClientSubscription(requestId, symbol));
        log.info("Subscribed FIX session to prices: session={}, requestId={}, symbol={}",
            session.compositeKey(), requestId, symbol);
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
        clientSubscriptions.remove(session);
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

    private void onPricingFragment(
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final io.aeron.logbuffer.Header header) {
        orderBookCodec.decode(buffer, offset, orderBook);
        for (Map.Entry<Session, ClientSubscription> entry : clientSubscriptions.entrySet()) {
            if (matchesSymbol(entry.getValue().symbol())) {
                sendSnapshot(entry.getKey(), entry.getValue().requestId());
            }
        }
    }

    private boolean matchesSymbol(final String requestedSymbol) {
        if (requestedSymbol.length() != orderBook.getSymbolLength()) {
            return false;
        }
        for (int index = 0; index < requestedSymbol.length(); index++) {
            if (requestedSymbol.charAt(index) != orderBook.getSymbol()[index]) {
                return false;
            }
        }
        return true;
    }

    private void sendSnapshot(final Session session, final String requestId) {
        snapshot.reset();
        snapshot.mDReqID(requestId);
        snapshot.instrument().symbol(orderBook.getSymbol(), orderBook.getSymbolLength());
        snapshot.sourceSentTimeNs(orderBook.getSentTimestamp());

        final int levelCount = orderBook.getOrderBookLength();
        MarketDataSnapshotFullRefreshEncoder.MDEntriesGroupEncoder entries =
            snapshot.mDEntriesGroup(levelCount * 2);
        for (int index = 0; index < levelCount; index++) {
            entries.mDEntryType(MDEntryType.BID)
                .mDEntryPx(orderBook.getBids()[index], orderBook.getPricePrecision())
                .mDEntrySize(orderBook.getBidSizes()[index], 0);
            entries = entries.next();
            entries.mDEntryType(MDEntryType.OFFER)
                .mDEntryPx(orderBook.getAsks()[index], orderBook.getPricePrecision())
                .mDEntrySize(orderBook.getAskSizes()[index], 0);
            if (index + 1 < levelCount) {
                entries = entries.next();
            }
        }

        final long position = session.trySend(snapshot);
        if (position < 0) {
            log.warn("Unable to send price snapshot: session={}, result={}", session.compositeKey(), position);
        }
    }

    private record ClientSubscription(String requestId, String symbol) {
    }
}
