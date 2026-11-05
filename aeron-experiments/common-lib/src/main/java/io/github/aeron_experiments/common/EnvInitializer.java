package io.github.aeron_experiments.common;

import io.aeron.Aeron;
import io.aeron.archive.Archive;

import java.util.Map;

public final class EnvInitializer {
    private static final String[] ENV_PREFIXES = {"AERON_", "ARTIO_", "PERF_"};

    private EnvInitializer() {
    }

    public static void init() {
        Map<String, String> envs = System.getenv();

        String aeronMdGroupId = System.getenv("PERF_AERON_MD_GROUP_ID");
        if (aeronMdGroupId != null) {
            System.setProperty(Aeron.Context.AERON_DIR_PROP_NAME,
                    Aeron.Context.AERON_DIR_PROP_DEFAULT + "-" + aeronMdGroupId);
            System.setProperty(Archive.Configuration.ARCHIVE_DIR_PROP_NAME,
                    Archive.Configuration.ARCHIVE_DIR_DEFAULT + "-" + aeronMdGroupId);
        }

        envs.forEach((envKey, envValue) -> {
            for (String prefix : ENV_PREFIXES) {
                if (envKey.startsWith(prefix)) {
                    System.getProperties()
                            .putIfAbsent(envKey.toLowerCase().replace("_", "."), envValue);
                }
            }
        });
    }
}
