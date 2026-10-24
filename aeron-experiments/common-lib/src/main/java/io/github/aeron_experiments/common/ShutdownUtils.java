package io.github.aeron_experiments.common;

public final class ShutdownUtils {
    private ShutdownUtils() {
    }

    public static void registerShutdownHook(Runnable shutdownHook) {
        Runtime.getRuntime().addShutdownHook(new Thread(shutdownHook));
    }

}
