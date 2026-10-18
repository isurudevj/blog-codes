package io.github.aeron_experiments.codec_utils;

import lombok.Data;

@Data
public class OrderBookSnapshot {

    private char[] symbol;
    private short symbolLength;
    private short pricePrecision;
    private long sentTimestamp;
    private long[] bids;
    private long[] asks;
    private long[] bidSizes;
    private long[] askSizes;
    private int orderBookLength;

}
