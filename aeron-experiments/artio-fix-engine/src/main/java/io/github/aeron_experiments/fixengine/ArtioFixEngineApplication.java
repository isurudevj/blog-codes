package io.github.aeron_experiments.fixengine;

import io.github.aeron_experiments.artio_codecs.FixDictionaryImpl;
import io.github.aeron_experiments.artio_support.CustomEngineConfiguration;
import io.github.aeron_experiments.common.EnvInitializer;
import io.github.aeron_experiments.common.ShutdownUtils;
import io.github.aeron_experiments.common.ThreadFactoryUtils;
import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingIdleStrategy;
import uk.co.real_logic.artio.engine.FixEngine;

@Slf4j
public final class ArtioFixEngineApplication {

    public static void main(final String[] args) {
        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier()) {
            EnvInitializer.init();
            final CustomEngineConfiguration configuration = new CustomEngineConfiguration();

            FixEngine engine = FixEngine.launch(configuration.engineConfiguration(FixDictionaryImpl.class));
            log.info("Artio FIX engine started: bind={}:{}, aeronDir={}, archiveLogging=true, logDir={}",
                    configuration.bindToHost(), configuration.bindToPort(),
                    configuration.aeronDirectoryName(), configuration.logFileDir());
            log.info("Archive pruning enabled={}, intervalMs={}", configuration.archivePruningEnabled(),
                    configuration.archivePruningIntervalMs());

            ShutdownUtils.registerShutdownHook(barrier::signal);

            AgentRunner pruneAgentRunner = null;
            if (configuration.archivePruningEnabled()) {
                pruneAgentRunner = new AgentRunner(new SleepingIdleStrategy(),
                        throwable -> log.error("Error occurred", throwable),
                        null,
                        new ArchivePruningAgent(engine, configuration.archivePruningIntervalMs()));

                AgentRunner.startOnThread(pruneAgentRunner, ThreadFactoryUtils.createDemonThreadFactory());
            }


            barrier.await();
            CloseHelper.quietClose(pruneAgentRunner);
            CloseHelper.quietClose(engine);
        }
    }
}
