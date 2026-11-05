package io.github.aeron_experiments.fixengine;

import io.aeron.Aeron;
import uk.co.real_logic.artio.FixDictionaryImpl;
import uk.co.real_logic.artio.engine.EngineConfiguration;
import uk.co.real_logic.artio.engine.FixEngine;
import uk.co.real_logic.artio.messages.InitialAcceptedSessionOwner;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArtioFixEngineApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtioFixEngineApplication.class);
    private static final String ARCHIVE_CHANNEL = "aeron:ipc";

    private ArtioFixEngineApplication() {
    }

    public static void main(final String[] args) {
        final String host = env("FIX_ACCEPTOR_HOST", "0.0.0.0");
        final int port = Integer.parseInt(env("FIX_ACCEPTOR_PORT", "9876"));
        final String aeronDirectory = env("AERON_DIR", Aeron.Context.getAeronDirectoryName());
        final String libraryChannel = env("ARTIO_LIBRARY_CHANNEL", "aeron:ipc");
        final String logDirectory = env("ARTIO_LOG_DIR", "data/artio");
        final boolean archivePruningEnabled = Boolean.parseBoolean(
            env("ARTIO_ARCHIVE_PRUNING_ENABLED", "true"));

        final EngineConfiguration configuration = new EngineConfiguration()
            .bindTo(host, port)
            .libraryAeronChannel(libraryChannel)
            .logFileDir(logDirectory)
            .logInboundMessages(true)
            .logOutboundMessages(true)
            .initialAcceptedSessionOwner(InitialAcceptedSessionOwner.SOLE_LIBRARY)
            .acceptorfixDictionary(FixDictionaryImpl.class);
        configuration.aeronContext().aeronDirectoryName(aeronDirectory);
        configuration.aeronArchiveContext()
            .aeronDirectoryName(aeronDirectory)
            .controlRequestChannel(ARCHIVE_CHANNEL)
            .controlResponseChannel(ARCHIVE_CHANNEL)
            .recordingEventsChannel(ARCHIVE_CHANNEL);

        try (FixEngine engine = FixEngine.launch(configuration);
             AgentRunner pruningRunner = archivePruningEnabled ? startArchivePruningAgent(engine) : null;
             ShutdownSignalBarrier barrier = new ShutdownSignalBarrier()) {
            LOGGER.info("Artio FIX engine started: bind={}:{}, aeronDir={}, archiveLogging=true, logDir={}",
                host, port, aeronDirectory, logDirectory);
            LOGGER.info("Continuous archive pruning enabled={}", archivePruningEnabled);
            barrier.await();
        }
    }

    private static AgentRunner startArchivePruningAgent(final FixEngine engine) {
        final AgentRunner runner = new AgentRunner(
            new BusySpinIdleStrategy(),
            throwable -> LOGGER.error("Archive pruning agent failed", throwable),
            null,
            new ArchivePruningAgent(engine));
        AgentRunner.startOnThread(runner, runnable -> {
            final Thread thread = new Thread(runnable, "artio-archive-pruner");
            thread.setDaemon(true);
            return thread;
        });
        return runner;
    }

    private static String env(final String name, final String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }
}
