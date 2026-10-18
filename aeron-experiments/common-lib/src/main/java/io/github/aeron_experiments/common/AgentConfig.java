package io.github.aeron_experiments.common;

import lombok.experimental.UtilityClass;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;

import java.util.Locale;

@UtilityClass
public class AgentConfig {
    private static final String IDLE_STRATEGY_ENV_KEY = "AGENT_IDLE_STRATEGY";
    private static final String IDLE_STRATEGY_DEFAULT = "busy-spin";

    public static IdleStrategy idleStrategy() {
        return idleStrategy(System.getenv().getOrDefault(IDLE_STRATEGY_ENV_KEY, IDLE_STRATEGY_DEFAULT));
    }

    public static IdleStrategy idleStrategy(final String configuredValue) {
        final String strategy = configuredValue.trim().toLowerCase(Locale.ROOT);
        return switch (strategy) {
            case "busy-spin", "spin" -> new BusySpinIdleStrategy();
            case "yield", "yielding" -> new YieldingIdleStrategy();
            case "backoff" -> new BackoffIdleStrategy();
            case "sleep", "sleeping" -> new SleepingIdleStrategy();
            case "sleep-ms", "sleeping-ms" -> new SleepingMillisIdleStrategy();
            case "noop", "no-op" -> new NoOpIdleStrategy();
            default -> throw new IllegalArgumentException(
                "Unsupported " + IDLE_STRATEGY_ENV_KEY + ": " + configuredValue +
                    ". Expected busy-spin, yielding, backoff, sleeping, sleeping-ms, or noop");
        };
    }
}
