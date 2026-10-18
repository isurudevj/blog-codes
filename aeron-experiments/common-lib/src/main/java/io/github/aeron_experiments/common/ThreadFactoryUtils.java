package io.github.aeron_experiments.common;

import lombok.experimental.UtilityClass;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@UtilityClass
public class ThreadFactoryUtils {

    public static ThreadFactory createDemonThreadFactory(final String prefix) {
        return createThreadFactory(prefix, true);
    }

    public static ThreadFactory createThreadFactory(final String prefix, final boolean isDeamon) {
        final String validatedPrefix = Objects.requireNonNull(prefix, "prefix").trim();
        if (validatedPrefix.isEmpty()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }

        final AtomicInteger threadNumber = new AtomicInteger(1);
        return runnable -> {
            final Thread thread = new Thread(
                Objects.requireNonNull(runnable, "runnable"),
                validatedPrefix + "-" + threadNumber.getAndIncrement());
            thread.setDaemon(isDeamon);
            return thread;
        };
    }
}
