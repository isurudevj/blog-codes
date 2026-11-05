package io.github.aeron_experiments.artio_support;

import io.aeron.Aeron;
import uk.co.real_logic.artio.FixDictionaryImpl;
import uk.co.real_logic.artio.engine.EngineConfiguration;
import uk.co.real_logic.artio.messages.InitialAcceptedSessionOwner;

public final class CustomEngineConfiguration {
    public static final String BIND_HOST_PROP = "artio.bind.host";
    public static final String BIND_PORT_PROP = "artio.bind.port";
    public static final String LIBRARY_AERON_CHANNEL_PROP = "artio.library.aeron.channel";
    public static final String LOG_FILE_DIR_PROP = "artio.log.file.dir";
    public static final String LOG_INBOUND_MESSAGES_PROP = "artio.log.inbound.messages";
    public static final String LOG_OUTBOUND_MESSAGES_PROP = "artio.log.outbound.messages";
    public static final String ARCHIVE_CONTROL_REQUEST_CHANNEL_PROP =
        "artio.archive.control.request.channel";
    public static final String ARCHIVE_CONTROL_RESPONSE_CHANNEL_PROP =
        "artio.archive.control.response.channel";
    public static final String ARCHIVE_RECORDING_EVENTS_CHANNEL_PROP =
        "artio.archive.recording.events.channel";
    public static final String ARCHIVE_PRUNING_ENABLED_PROP = "artio.archive.pruning.enabled";

    private static final String DEFAULT_AERON_CHANNEL = "aeron:ipc";

    private final String bindToHost;
    private final int bindToPort;
    private final String aeronDirectoryName;
    private final String libraryAeronChannel;
    private final String logFileDir;
    private final boolean logInboundMessages;
    private final boolean logOutboundMessages;
    private final String archiveControlRequestChannel;
    private final String archiveControlResponseChannel;
    private final String archiveRecordingEventsChannel;
    private final boolean archivePruningEnabled;

    public CustomEngineConfiguration() {
        bindToHost = System.getProperty(BIND_HOST_PROP, "0.0.0.0");
        bindToPort = Integer.getInteger(BIND_PORT_PROP, 9876);
        aeronDirectoryName = Aeron.Context.getAeronDirectoryName();
        libraryAeronChannel = System.getProperty(LIBRARY_AERON_CHANNEL_PROP, DEFAULT_AERON_CHANNEL);
        logFileDir = System.getProperty(LOG_FILE_DIR_PROP, "data/artio");
        logInboundMessages = booleanProperty(LOG_INBOUND_MESSAGES_PROP, true);
        logOutboundMessages = booleanProperty(LOG_OUTBOUND_MESSAGES_PROP, true);
        archiveControlRequestChannel = System.getProperty(
            ARCHIVE_CONTROL_REQUEST_CHANNEL_PROP, DEFAULT_AERON_CHANNEL);
        archiveControlResponseChannel = System.getProperty(
            ARCHIVE_CONTROL_RESPONSE_CHANNEL_PROP, DEFAULT_AERON_CHANNEL);
        archiveRecordingEventsChannel = System.getProperty(
            ARCHIVE_RECORDING_EVENTS_CHANNEL_PROP, DEFAULT_AERON_CHANNEL);
        archivePruningEnabled = booleanProperty(ARCHIVE_PRUNING_ENABLED_PROP, true);
    }

    public EngineConfiguration engineConfiguration() {
        final EngineConfiguration configuration = new EngineConfiguration()
            .bindTo(bindToHost, bindToPort)
            .libraryAeronChannel(libraryAeronChannel)
            .logFileDir(logFileDir)
            .logInboundMessages(logInboundMessages)
            .logOutboundMessages(logOutboundMessages)
            .acceptorfixDictionary(FixDictionaryImpl.class);
        configuration.aeronContext().aeronDirectoryName(aeronDirectoryName);
        configuration.aeronArchiveContext()
            .aeronDirectoryName(aeronDirectoryName)
            .controlRequestChannel(archiveControlRequestChannel)
            .controlResponseChannel(archiveControlResponseChannel)
            .recordingEventsChannel(archiveRecordingEventsChannel);
        return configuration;
    }

    public String bindToHost() {
        return bindToHost;
    }

    public int bindToPort() {
        return bindToPort;
    }

    public String aeronDirectoryName() {
        return aeronDirectoryName;
    }

    public String logFileDir() {
        return logFileDir;
    }

    public boolean archivePruningEnabled() {
        return archivePruningEnabled;
    }

    private static boolean booleanProperty(final String name, final boolean defaultValue) {
        return Boolean.parseBoolean(System.getProperty(name, Boolean.toString(defaultValue)));
    }
}
