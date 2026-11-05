package io.github.aeron_experiments.qfjclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Application;
import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.FileLogFactory;
import quickfix.FileStoreFactory;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.RejectLogon;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.SessionSettings;
import quickfix.SocketInitiator;
import quickfix.UnsupportedMessageType;
import quickfix.field.MDEntryType;
import quickfix.field.MDReqID;
import quickfix.field.MarketDepth;
import quickfix.field.SubscriptionRequestType;
import quickfix.field.Symbol;
import quickfix.fix44.MarketDataRequest;
import quickfix.fix44.MarketDataSnapshotFullRefresh;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

public final class QfjPriceClientApplication implements Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(QfjPriceClientApplication.class);
    private static final String SETTINGS_RESOURCE = "/quickfixj-client.cfg";
    private final String symbol = System.getenv().getOrDefault("PRICE_SYMBOL", "AAPL");

    public static void main(final String[] args) throws Exception {
        final QfjPriceClientApplication application = new QfjPriceClientApplication();
        final SessionSettings settings;
        try (InputStream input = QfjPriceClientApplication.class.getResourceAsStream(SETTINGS_RESOURCE)) {
            if (input == null) {
                throw new ConfigError("Missing " + SETTINGS_RESOURCE);
            }
            settings = new SessionSettings(input);
        }

        final SocketInitiator initiator = new SocketInitiator(
            application,
            new FileStoreFactory(settings),
            settings,
            new FileLogFactory(settings),
            new DefaultMessageFactory());
        Runtime.getRuntime().addShutdownHook(new Thread(initiator::stop, "qfj-shutdown"));
        initiator.start();
        LOGGER.info("QuickFIX/J price client started; requesting symbol={}", application.symbol);
        new CountDownLatch(1).await();
    }

    @Override
    public void onCreate(final SessionID sessionId) {
        LOGGER.info("Created FIX session {}", sessionId);
    }

    @Override
    public void onLogon(final SessionID sessionId) {
        LOGGER.info("Logged on to {}", sessionId);
        final MarketDataRequest request = new MarketDataRequest(
            new MDReqID("prices-" + symbol),
            new SubscriptionRequestType(SubscriptionRequestType.SNAPSHOT_UPDATES),
            new MarketDepth(1));
        request.addGroup(entryType(MDEntryType.BID));
        request.addGroup(entryType(MDEntryType.OFFER));
        final MarketDataRequest.NoRelatedSym relatedSymbol = new MarketDataRequest.NoRelatedSym();
        relatedSymbol.set(new Symbol(symbol));
        request.addGroup(relatedSymbol);
        try {
            Session.sendToTarget(request, sessionId);
        } catch (SessionNotFound exception) {
            LOGGER.error("Unable to send MarketDataRequest on {}", sessionId, exception);
        }
    }

    private static MarketDataRequest.NoMDEntryTypes entryType(final char type) {
        final MarketDataRequest.NoMDEntryTypes group = new MarketDataRequest.NoMDEntryTypes();
        group.set(new MDEntryType(type));
        return group;
    }

    @Override
    public void onLogout(final SessionID sessionId) {
        LOGGER.info("Logged out from {}", sessionId);
    }

    @Override
    public void toAdmin(final Message message, final SessionID sessionId) {
        LOGGER.debug("FIX admin outbound: {}", message);
    }

    @Override
    public void fromAdmin(final Message message, final SessionID sessionId)
        throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        LOGGER.debug("FIX admin inbound: {}", message);
    }

    @Override
    public void toApp(final Message message, final SessionID sessionId) throws DoNotSend {
        LOGGER.info("FIX application outbound: {}", message);
    }

    @Override
    public void fromApp(final Message message, final SessionID sessionId)
        throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        if (message instanceof MarketDataSnapshotFullRefresh snapshot) {
            LOGGER.info("Price snapshot: symbol={}, entries={}, message={}",
                snapshot.getString(Symbol.FIELD), snapshot.getGroupCount(quickfix.field.NoMDEntries.FIELD), snapshot);
        } else {
            LOGGER.info("FIX application inbound: {}", message);
        }
    }
}
