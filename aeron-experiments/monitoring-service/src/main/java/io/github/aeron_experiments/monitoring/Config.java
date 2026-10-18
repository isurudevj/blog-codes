package io.github.aeron_experiments.monitoring;

import io.aeron.CommonContext;
import lombok.experimental.UtilityClass;

import java.io.File;

@UtilityClass
public class Config {
    private static final String INTERVAL_SECONDS_ENV_KEY = "MONITOR_INTERVAL_SECONDS";
    private static final long INTERVAL_SECONDS_DEFAULT = 5;
    private static final String METRICS_HOST_ENV_KEY = "METRICS_HOST";
    private static final String METRICS_HOST_DEFAULT = "127.0.0.1";
    private static final String METRICS_PORT_ENV_KEY = "METRICS_PORT";
    private static final int METRICS_PORT_DEFAULT = 9100;

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

    public static String metricsHost() {
        return System.getenv().getOrDefault(METRICS_HOST_ENV_KEY, METRICS_HOST_DEFAULT);
    }

    public static int metricsPort() {
        final int port = Integer.parseInt(
            System.getenv().getOrDefault(METRICS_PORT_ENV_KEY, Integer.toString(METRICS_PORT_DEFAULT)));
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException(METRICS_PORT_ENV_KEY + " must be between 1 and 65535");
        }
        return port;
    }
}
