package io.github.aeron_experiments.common;

import io.aeron.Aeron;

import java.util.Map;

public final class EnvInitializer {
    private static final String ENV_PREFIX = "AERON_";

    private EnvInitializer() {
    }

    /**
     * Copies {@code AERON_*} environment variables to Aeron system properties.
     * Existing system properties are not overwritten, allowing explicit
     * {@code -D} arguments to take precedence over environment variables.
     *
     * <p>For example, {@code AERON_CONDUCTOR_IDLE_STRATEGY} becomes
     * {@code aeron.conductor.idle.strategy}.</p>
     */
    public static void init() {
        Map<String, String> envs = System.getenv();

        String aeronMdGroupId = System.getenv("PERF_AERON_MD_GROUP_ID");
        if (aeronMdGroupId != null) {
            System.setProperty(Aeron.Context.AERON_DIR_PROP_NAME,
                    Aeron.Context.AERON_DIR_PROP_DEFAULT + "-" + aeronMdGroupId);
        }

        envs.forEach((envKey, envValue) -> {
            if (envKey.startsWith(ENV_PREFIX)) {
                System.getProperties().putIfAbsent(envKey.toLowerCase().replace("_", "."), envValue);
            }
        });
    }
}
