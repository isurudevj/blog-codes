package io.github.aeron_experiments.artiopriceclient;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Config {
    public static String applicationName() {
        return System.getenv().getOrDefault("APP_NAME", "artio-price-client-1");
    }

    public static String fixHost() {
        return System.getenv().getOrDefault("FIX_HOST", "127.0.0.1");
    }

    public static int fixPort() {
        return Integer.parseInt(System.getenv().getOrDefault("FIX_PORT", "21304"));
    }

    public static String senderCompId() {
        return System.getenv().getOrDefault("FIX_SENDER_COMP_ID", "ARTIO_PRICE_CLIENT");
    }

    public static String targetCompId() {
        return System.getenv().getOrDefault("FIX_TARGET_COMP_ID", "PRICE_DISTRIBUTOR");
    }

    public static String symbol() {
        return System.getenv().getOrDefault("PRICE_SYMBOL", "AAPL");
    }
}
