package io.github.aeron_experiments.artiopriceclient;

import io.aeron.Aeron;
import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import io.github.aeron_experiments.artio_codecs.FixDictionaryImpl;
import io.github.aeron_experiments.artio_codecs.MDEntryType;
import io.github.aeron_experiments.artio_codecs.SubscriptionRequestType;
import io.github.aeron_experiments.artio_codecs.builder.MarketDataRequestEncoder;
import io.github.aeron_experiments.artio_codecs.decoder.MarketDataSnapshotFullRefreshDecoder;
import io.github.aeron_experiments.common.ThreadFactoryUtils;
import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.HdrHistogram.Histogram;
import uk.co.real_logic.artio.Reply;
import uk.co.real_logic.artio.library.FixLibrary;
import uk.co.real_logic.artio.library.LibraryConfiguration;
import uk.co.real_logic.artio.library.LibraryConnectHandler;
import uk.co.real_logic.artio.library.OnMessageInfo;
import uk.co.real_logic.artio.library.SessionAcquireHandler;
import uk.co.real_logic.artio.library.SessionAcquiredInfo;
import uk.co.real_logic.artio.library.SessionConfiguration;
import uk.co.real_logic.artio.library.SessionHandler;
import uk.co.real_logic.artio.messages.DisconnectReason;
import uk.co.real_logic.artio.session.Session;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

import java.util.List;

@Slf4j
final class ArtioPriceClientAgent implements Agent, SessionHandler, SessionAcquireHandler, LibraryConnectHandler {
    private static final long REPORT_INTERVAL_NS = 1_000_000_000L;
    private static final long MAX_TRACKABLE_LATENCY_NS = 60_000_000_000L;
    private static final int LATENCY_SIGNIFICANT_DIGITS = 3;

    private final MarketDataRequestEncoder request = new MarketDataRequestEncoder();
    private final MarketDataSnapshotFullRefreshDecoder snapshot = new MarketDataSnapshotFullRefreshDecoder();
    private final MutableAsciiBuffer asciiBuffer = new MutableAsciiBuffer();
    private final Histogram intervalLatency = new Histogram(
        1, MAX_TRACKABLE_LATENCY_NS, LATENCY_SIGNIFICANT_DIGITS);
    private FixLibrary library;
    private Reply<Session> initiateReply;
    private long reportStartNs = System.nanoTime();
    private long intervalMessages;
    private long totalMessages;

    @Override
    public void onStart() {
        final LibraryConfiguration configuration = new LibraryConfiguration();
        configuration.libraryName(Config.applicationName())
            .sessionAcquireHandler(this)
            .libraryConnectHandler(this)
            .libraryAeronChannels(List.of(Aeron.Context.IPC_CHANNEL));
        configuration.threadFactory(ThreadFactoryUtils.createDemonThreadFactory(Config.applicationName()));
        library = FixLibrary.connect(configuration);
    }

    @Override
    public int doWork() {
        final int work = library.poll(10);
        if (initiateReply != null && !initiateReply.isExecuting() && !initiateReply.hasCompleted()) {
            log.error("Unable to initiate FIX session: state={}, error={}",
                initiateReply.state(), initiateReply.error());
            initiateReply = null;
        }
        reportIfDue();
        return work;
    }

    @Override
    public void onConnect(final FixLibrary connectedLibrary) {
        log.info("Connected to md-2 FIX engine; initiating {} -> {} at {}:{}",
            Config.senderCompId(), Config.targetCompId(), Config.fixHost(), Config.fixPort());
        initiateReply = connectedLibrary.initiate(SessionConfiguration.builder()
            .address(Config.fixHost(), Config.fixPort())
            .senderCompId(Config.senderCompId())
            .targetCompId(Config.targetCompId())
            .resetSeqNum(true)
            .fixDictionary(FixDictionaryImpl.class)
            .build());
    }

    @Override
    public void onDisconnect(final FixLibrary disconnectedLibrary) {
        log.warn("Disconnected from md-2 FIX engine");
        initiateReply = null;
    }

    @Override
    public SessionHandler onSessionAcquired(final Session session, final SessionAcquiredInfo acquiredInfo) {
        log.info("Acquired FIX session {} -> {}", session.compositeKey().localCompId(),
            session.compositeKey().remoteCompId());
        return this;
    }

    @Override
    public void onSessionStart(final Session session) {
        request.reset();
        request.mDReqID("prices-" + Config.symbol())
            .subscriptionRequestType(SubscriptionRequestType.SNAPSHOT_PLUS_UPDATES)
            .marketDepth(0);
        request.mDEntryTypesGroup(2).mDEntryType(MDEntryType.BID).next().mDEntryType(MDEntryType.OFFER);
        request.relatedSymGroup(1).instrument().symbol(Config.symbol());
        final long result = session.trySend(request);
        if (result < 0) {
            log.error("Unable to send market data request: result={}", result);
        } else {
            log.info("Subscribed to FIX prices for symbol={}", Config.symbol());
        }
    }

    @Override
    public Action onMessage(
        final DirectBuffer buffer, final int offset, final int length, final int libraryId,
        final Session session, final int sequenceIndex, final long messageType, final long timestampInNs,
        final long position, final OnMessageInfo messageInfo) {
        if (messageType == MarketDataSnapshotFullRefreshDecoder.MESSAGE_TYPE) {
            asciiBuffer.wrap(buffer);
            snapshot.decode(asciiBuffer, offset, length);
            final long latencyNs = System.nanoTime() - snapshot.sourceSentTimeNs();
            if (latencyNs >= 0 && latencyNs <= MAX_TRACKABLE_LATENCY_NS) {
                intervalLatency.recordValue(latencyNs);
            } else {
                log.warn("Latency outside histogram range: latencyNs={}, sourceSentTimeNs={}",
                    latencyNs, snapshot.sourceSentTimeNs());
            }
            intervalMessages++;
            totalMessages++;
        }
        return Action.CONTINUE;
    }

    private void reportIfDue() {
        final long nowNs = System.nanoTime();
        final long elapsedNs = nowNs - reportStartNs;
        if (elapsedNs >= REPORT_INTERVAL_NS) {
            final long messagesPerSecond = intervalMessages * REPORT_INTERVAL_NS / elapsedNs;
            if (intervalLatency.getTotalCount() == 0) {
                log.info("FIX price rate={} msg/s, latencySamples=0, totalMessages={}",
                    messagesPerSecond, totalMessages);
            } else {
                log.info(
                    "FIX price rate={} msg/s, latencyP99={} ns, latencyP99.9={} ns, " +
                        "latencyP100={} ns, latencySamples={}, totalMessages={}",
                    messagesPerSecond,
                    intervalLatency.getValueAtPercentile(99.0),
                    intervalLatency.getValueAtPercentile(99.9),
                    intervalLatency.getMaxValue(),
                    intervalLatency.getTotalCount(),
                    totalMessages);
            }
            reportStartNs = nowNs;
            intervalMessages = 0;
            intervalLatency.reset();
        }
    }

    @Override
    public void onTimeout(final int libraryId, final Session session) {
        log.warn("FIX session timed out: {}", session.compositeKey());
    }

    @Override
    public void onSlowStatus(final int libraryId, final Session session, final boolean hasBecomeSlow) {
        log.warn("FIX session slow status: session={}, slow={}", session.compositeKey(), hasBecomeSlow);
    }

    @Override
    public Action onDisconnect(final int libraryId, final Session session, final DisconnectReason reason) {
        log.warn("FIX session disconnected: session={}, reason={}", session.compositeKey(), reason);
        return Action.CONTINUE;
    }

    @Override
    public void onClose() {
        CloseHelper.quietClose(library);
    }

    @Override
    public String roleName() {
        return "artio-price-client";
    }
}
