package io.github.aeron_experiments.common;

import org.agrona.concurrent.IdleStrategy;

import java.util.concurrent.locks.LockSupport;

/**
 * A deliberately slow consumer profile which pauses after every agent work cycle.
 */
public final class CustomSlow1IdleStrategy implements IdleStrategy {
    public static final String ALIAS = "custom-slow-1";
    public static final long SLEEP_PERIOD_NS = 200_000L;

    @Override
    public void idle(final int workCount) {
        LockSupport.parkNanos(SLEEP_PERIOD_NS);
    }

    @Override
    public void idle() {
        LockSupport.parkNanos(SLEEP_PERIOD_NS);
    }

    @Override
    public void reset() {
        // This strategy has no state to reset.
    }

    @Override
    public String alias() {
        return ALIAS;
    }
}
