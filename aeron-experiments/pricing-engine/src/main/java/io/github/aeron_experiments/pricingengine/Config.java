package io.github.aeron_experiments.pricingengine;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Config {
    private static final String APP_NAME_DEFAULT = "pricing-engine-1";
    private static final String APP_NAME_ENV_KEY = "APP_NAME";
    private static final String PRICING_CHANNEL_DEFAULT = "aeron:ipc";
    private static final String PRICING_CHANNEL_ENV_KEY = "PRICING_CHANNEL";
    private static final int PRICING_STREAM_ID_DEFAULT = 1001;
    private static final String PRICING_STREAM_ID_ENV_KEY = "PRICING_STREAM_ID";
    private static final int MESSAGES_PER_SECOND_DEFAULT = 1_000;
    private static final String MESSAGES_PER_SECOND_ENV_KEY = "MESSAGES_PER_SECOND";

    public static int pricingStreamId() {
        return Integer.parseInt(
            System.getenv().getOrDefault(PRICING_STREAM_ID_ENV_KEY, Integer.toString(PRICING_STREAM_ID_DEFAULT)));
    }

    public static String pricingChannel() {
        return System.getenv().getOrDefault(PRICING_CHANNEL_ENV_KEY, PRICING_CHANNEL_DEFAULT);
    }

    public static String applicationName() {
        return System.getenv().getOrDefault(APP_NAME_ENV_KEY, APP_NAME_DEFAULT);
    }

    public static int messagesPerSecond() {
        final int value = Integer.parseInt(System.getenv().getOrDefault(
            MESSAGES_PER_SECOND_ENV_KEY, Integer.toString(MESSAGES_PER_SECOND_DEFAULT)));
        if (value <= 0 || value > 1_000_000_000) {
            throw new IllegalArgumentException("MESSAGES_PER_SECOND must be between 1 and 1000000000");
        }
        return value;
    }
}
