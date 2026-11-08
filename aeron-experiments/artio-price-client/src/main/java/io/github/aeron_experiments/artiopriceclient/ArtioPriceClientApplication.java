package io.github.aeron_experiments.artiopriceclient;

import io.github.aeron_experiments.common.AgentConfig;
import io.github.aeron_experiments.common.EnvInitializer;
import io.github.aeron_experiments.common.ShutdownUtils;
import io.github.aeron_experiments.common.ThreadFactoryUtils;
import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ShutdownSignalBarrier;

@Slf4j
public final class ArtioPriceClientApplication {
    private ArtioPriceClientApplication() {
    }

    public static void main(final String[] args) {
        EnvInitializer.init();
        try (ShutdownSignalBarrier barrier = new ShutdownSignalBarrier()) {
            final AgentRunner runner = new AgentRunner(
                AgentConfig.idleStrategy(),
                throwable -> log.error("Artio price client failed", throwable),
                null,
                new ArtioPriceClientAgent());
            AgentRunner.startOnThread(runner, ThreadFactoryUtils.createDemonThreadFactory(Config.applicationName()));
            ShutdownUtils.registerShutdownHook(barrier::signal);
            log.info("Artio price client started: appName={}, FIX={}:{}, sender={}, target={}, symbol={}",
                Config.applicationName(), Config.fixHost(), Config.fixPort(), Config.senderCompId(),
                Config.targetCompId(), Config.symbol());
            barrier.await();
            CloseHelper.quietClose(runner);
        }
    }
}
