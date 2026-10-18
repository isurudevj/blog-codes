package io.github.aeron_experiments.monitoring;

import io.aeron.CommonContext;
import lombok.experimental.UtilityClass;

import java.io.File;

@UtilityClass
public class Config {
    private static final String INTERVAL_SECONDS_ENV_KEY = "MONITOR_INTERVAL_SECONDS";
    private static final long INTERVAL_SECONDS_DEFAULT = 5;

    public static long intervalSeconds() {
        final long interval = Long.parseLong(
            System.getenv().getOrDefault(INTERVAL_SECONDS_ENV_KEY, Long.toString(INTERVAL_SECONDS_DEFAULT)));
        if (interval <= 0) {
            throw new IllegalArgumentException(INTERVAL_SECONDS_ENV_KEY + " must be greater than zero");
        }
        return interval;
    }

    public static long intervalMs() {
        return Math.multiplyExact(intervalSeconds(), 1_000L);
    }

    public static File aeronDirectory() {
        return new File(CommonContext.getAeronDirectoryName());
    }
}
