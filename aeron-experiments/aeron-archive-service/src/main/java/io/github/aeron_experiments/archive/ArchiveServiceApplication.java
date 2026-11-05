package io.github.aeron_experiments.archive;

import io.aeron.Aeron;
import io.aeron.archive.Archive;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public final class ArchiveServiceApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveServiceApplication.class);
    private static final String IPC_CHANNEL = "aeron:ipc";
    private static final String CONTROL_CHANNEL = "aeron:udp?endpoint=localhost:8010";

    private ArchiveServiceApplication() {
    }

    public static void main(final String[] args) {
        final String aeronDirectory = env("AERON_DIR", Aeron.Context.getAeronDirectoryName());
        final File archiveDirectory = new File(env("AERON_ARCHIVE_DIR", "data/archive"));
        final Archive.Context context = new Archive.Context()
            .aeronDirectoryName(aeronDirectory)
            .archiveDir(archiveDirectory)
            .controlChannel(CONTROL_CHANNEL)
            .localControlChannel(IPC_CHANNEL)
            .recordingEventsChannel(IPC_CHANNEL)
            .replicationChannel("aeron:udp?endpoint=localhost:0");

        try (Archive archive = Archive.launch(context);
             ShutdownSignalBarrier barrier = new ShutdownSignalBarrier()) {
            LOGGER.info("Aeron Archive started: aeronDir={}, archiveDir={}",
                aeronDirectory, archiveDirectory.getAbsolutePath());
            barrier.await();
        }
    }

    private static String env(final String name, final String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }
}
