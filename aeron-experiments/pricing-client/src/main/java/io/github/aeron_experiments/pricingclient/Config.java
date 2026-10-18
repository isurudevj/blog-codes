package io.github.aeron_experiments.pricingclient;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Config {
    private static final String PRICING_CHANNEL_DEFAULT = "aeron:ipc";
    private static final String PRICING_CHANNEL_ENV_KEY = "PRICING_CHANNEL";
    private static final int PRICING_STREAM_ID_DEFAULT = 1001;
    private static final String PRICING_STREAM_ID_ENV_KEY = "PRICING_STREAM_ID";

    public static int pricingStreamId() {
        return Integer.parseInt(
            System.getenv().getOrDefault(PRICING_STREAM_ID_ENV_KEY, Integer.toString(PRICING_STREAM_ID_DEFAULT)));
    }

    public static String pricingChannel() {
        return System.getenv().getOrDefault(PRICING_CHANNEL_ENV_KEY, PRICING_CHANNEL_DEFAULT);
    }
}
