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
    private static final String IDLE_STRATEGY_DEFAULT = BusySpinIdleStrategy.ALIAS;

    public static IdleStrategy idleStrategy() {
        return idleStrategy(System.getenv().getOrDefault(IDLE_STRATEGY_ENV_KEY, IDLE_STRATEGY_DEFAULT));
    }

    public static IdleStrategy idleStrategy(final String configuredValue) {
        final String strategy = configuredValue.trim().toLowerCase(Locale.ROOT);
        return switch (strategy) {
            case BusySpinIdleStrategy.ALIAS, "busy-spin" -> new BusySpinIdleStrategy();
            case YieldingIdleStrategy.ALIAS, "yielding" -> new YieldingIdleStrategy();
            case BackoffIdleStrategy.ALIAS -> new BackoffIdleStrategy();
            case SleepingIdleStrategy.ALIAS, "sleep", "sleeping" -> new SleepingIdleStrategy();
            case SleepingMillisIdleStrategy.ALIAS, "sleeping-ms" -> new SleepingMillisIdleStrategy();
            case NoOpIdleStrategy.ALIAS, "no-op" -> new NoOpIdleStrategy();
            case CustomSlow1IdleStrategy.ALIAS -> new CustomSlow1IdleStrategy();
            default -> throw new IllegalArgumentException(
                "Unsupported " + IDLE_STRATEGY_ENV_KEY + ": " + configuredValue +
                    ". Expected " + BusySpinIdleStrategy.ALIAS + ", " + YieldingIdleStrategy.ALIAS + ", " +
                    BackoffIdleStrategy.ALIAS + ", " + SleepingIdleStrategy.ALIAS + ", " +
                    SleepingMillisIdleStrategy.ALIAS + ", " + NoOpIdleStrategy.ALIAS + ", or " +
                    CustomSlow1IdleStrategy.ALIAS);
        };
    }
}
