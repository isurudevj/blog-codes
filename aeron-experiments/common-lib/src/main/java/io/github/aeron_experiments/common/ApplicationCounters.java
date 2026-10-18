package io.github.aeron_experiments.common;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ApplicationCounters {
    public static final int APPLICATION_IDENTITY_TYPE_ID = 10_001;
    public static final String APPLICATION_IDENTITY_LABEL_PREFIX = "app-name: ";

    public static String identityLabel(final String applicationName) {
        return APPLICATION_IDENTITY_LABEL_PREFIX + applicationName;
    }
}
