package io.github.aeron_experiments.common;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadFactoryUtilsTest {

    @Test
    void shouldCreateNamedDaemonThreads() {
        final ThreadFactory factory = ThreadFactoryUtils.createDemonThreadFactory("pricing-engine");

        final Thread first = factory.newThread(() -> { });
        final Thread second = factory.newThread(() -> { });

        assertEquals("pricing-engine-1", first.getName());
        assertEquals("pricing-engine-2", second.getName());
        assertTrue(first.isDaemon());
        assertTrue(second.isDaemon());
    }

    @Test
    void shouldCreateNamedNonDaemonThreads() {
        final Thread thread = ThreadFactoryUtils
            .createThreadFactory("pricing-client", false)
            .newThread(() -> { });

        assertEquals("pricing-client-1", thread.getName());
        assertFalse(thread.isDaemon());
    }

    @Test
    void shouldRejectInvalidPrefixAndRunnable() {
        assertThrows(NullPointerException.class, () ->
            ThreadFactoryUtils.createThreadFactory(null, false));
        assertThrows(IllegalArgumentException.class, () ->
            ThreadFactoryUtils.createThreadFactory("  ", false));

        final ThreadFactory factory = ThreadFactoryUtils.createThreadFactory("worker", false);
        assertThrows(NullPointerException.class, () -> factory.newThread(null));
    }
}
