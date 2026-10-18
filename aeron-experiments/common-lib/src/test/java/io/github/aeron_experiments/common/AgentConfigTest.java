package io.github.aeron_experiments.common;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentConfigTest {

    @Test
    void shouldCreateSupportedIdleStrategies() {
        assertInstanceOf(BusySpinIdleStrategy.class, AgentConfig.idleStrategy("busy-spin"));
        assertInstanceOf(BusySpinIdleStrategy.class, AgentConfig.idleStrategy("spin"));
        assertInstanceOf(YieldingIdleStrategy.class, AgentConfig.idleStrategy("yielding"));
        assertInstanceOf(BackoffIdleStrategy.class, AgentConfig.idleStrategy("backoff"));
        assertInstanceOf(SleepingIdleStrategy.class, AgentConfig.idleStrategy("sleeping"));
        assertInstanceOf(SleepingMillisIdleStrategy.class, AgentConfig.idleStrategy("sleeping-ms"));
        assertInstanceOf(NoOpIdleStrategy.class, AgentConfig.idleStrategy("noop"));
    }

    @Test
    void shouldRejectUnsupportedIdleStrategy() {
        assertThrows(IllegalArgumentException.class, () -> AgentConfig.idleStrategy("unknown"));
    }
}
