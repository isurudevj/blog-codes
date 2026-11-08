package io.github.aeron_experiments.pricedistributor;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Config {
    private static final String APP_NAME_DEFAULT = "price-distributor-1";
    private static final String PRICING_CHANNEL_DEFAULT = "aeron:ipc";
    private static final int PRICING_STREAM_ID_DEFAULT = 1001;

    public static String applicationName() {
        return System.getenv().getOrDefault("APP_NAME", APP_NAME_DEFAULT);
    }

    public static String pricingChannel() {
        return System.getenv().getOrDefault("PRICING_CHANNEL", PRICING_CHANNEL_DEFAULT);
    }

    public static int pricingStreamId() {
        return Integer.parseInt(System.getenv().getOrDefault(
            "PRICING_STREAM_ID", Integer.toString(PRICING_STREAM_ID_DEFAULT)));
    }
}
