package io.github.aeron_experiments.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomSlow1IdleStrategyTest {

    @Test
    void shouldExposeSlowConsumerProfile() {
        final CustomSlow1IdleStrategy strategy = new CustomSlow1IdleStrategy();

        assertEquals("custom-slow-1", strategy.alias());
        assertEquals(200_000L, CustomSlow1IdleStrategy.SLEEP_PERIOD_NS);

        strategy.idle(1);
        strategy.idle(0);
        strategy.idle();
        strategy.reset();
    }
}
