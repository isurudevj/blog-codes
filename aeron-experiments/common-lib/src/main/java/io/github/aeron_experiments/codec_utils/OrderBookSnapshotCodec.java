package io.github.aeron_experiments.codec_utils;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Reusable adapter between {@link OrderBookSnapshot} objects and generated SBE flyweights.
 * The caller owns and reuses the snapshot, its arrays, and the underlying buffer.
 */
public final class OrderBookSnapshotCodec {
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final OrderBookSnapshotEncoder encoder = new OrderBookSnapshotEncoder();
    private final OrderBookSnapshotDecoder decoder = new OrderBookSnapshotDecoder();

    /**
     * Encodes a snapshot into an existing buffer without allocating.
     *
     * @return total encoded length, including the SBE message header
     */
    public int encode(
        final OrderBookSnapshot snapshot,
        final MutableDirectBuffer buffer,
        final int offset) {
        validateSnapshot(snapshot);

        encoder.wrapAndApplyHeader(buffer, offset, headerEncoder);
        encodeSymbol(snapshot);
        encoder.pricePrecision(snapshot.getPricePrecision());
        encoder.sentTimestamp(snapshot.getSentTimestamp());

        final int levelCount = snapshot.getOrderBookLength();
        final OrderBookSnapshotEncoder.OrderBookLevelsEncoder levels =
            encoder.orderBookLevelsCount(levelCount);
        final long[] bids = snapshot.getBids();
        final long[] asks = snapshot.getAsks();
        final long[] bidSizes = snapshot.getBidSizes();
        final long[] askSizes = snapshot.getAskSizes();

        for (int i = 0; i < levelCount; i++) {
            if (bidSizes[i] < 0 || askSizes[i] < 0) {
                throw new IllegalArgumentException("order book quantities cannot be negative");
            }
            levels.next()
                .bidPrice(bids[i])
                .bidQuantity(bidSizes[i])
                .askPrice(asks[i])
                .askQuantity(askSizes[i]);
        }

        return MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
    }

    /**
     * Decodes into a caller-supplied snapshot and returns the same instance.
     * All target arrays must be allocated before this method is called.
     */
    public OrderBookSnapshot decode(
        final DirectBuffer buffer,
        final int offset,
        final OrderBookSnapshot target) {
        requireTargetSymbolCapacity(target);
        decoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        if (headerDecoder.schemaId() != OrderBookSnapshotDecoder.SCHEMA_ID) {
            throw new IllegalArgumentException("unexpected SBE schema id: " + headerDecoder.schemaId());
        }

        decodeSymbol(target);
        target.setPricePrecision(decoder.pricePrecision());
        target.setSentTimestamp(decoder.sentTimestamp());

        final OrderBookSnapshotDecoder.OrderBookLevelsDecoder levels = decoder.orderBookLevels();
        final int levelCount = levels.count();
        requireLevelCapacity(target, levelCount);
        final long[] bids = target.getBids();
        final long[] asks = target.getAsks();
        final long[] bidSizes = target.getBidSizes();
        final long[] askSizes = target.getAskSizes();

        int index = 0;
        while (levels.hasNext()) {
            levels.next();
            bids[index] = levels.bidPrice();
            bidSizes[index] = levels.bidQuantity();
            asks[index] = levels.askPrice();
            askSizes[index] = levels.askQuantity();
            index++;
        }
        target.setOrderBookLength(levelCount);
        return target;
    }

    private void encodeSymbol(final OrderBookSnapshot snapshot) {
        final int symbolLength = snapshot.getSymbolLength();
        final char[] symbol = snapshot.getSymbol();
        for (int i = 0; i < symbolLength; i++) {
            final char value = symbol[i];
            if (value > 0x7F) {
                throw new IllegalArgumentException("symbol must contain only US-ASCII characters");
            }
            encoder.symbol(i, (byte)value);
        }
        for (int i = symbolLength; i < OrderBookSnapshotEncoder.symbolLength(); i++) {
            encoder.symbol(i, (byte)0);
        }
    }

    private void decodeSymbol(final OrderBookSnapshot target) {
        final char[] symbol = target.getSymbol();
        int symbolLength = 0;
        for (int i = 0; i < OrderBookSnapshotDecoder.symbolLength(); i++) {
            final byte value = decoder.symbol(i);
            symbol[i] = (char)(value & 0xFF);
            if (value != 0) {
                symbolLength = i + 1;
            }
        }
        target.setSymbolLength((short)symbolLength);
    }

    private static void validateSnapshot(final OrderBookSnapshot snapshot) {
        requireTargetSymbolCapacity(snapshot);
        final int symbolLength = snapshot.getSymbolLength();
        if (symbolLength < 0 || symbolLength > OrderBookSnapshotEncoder.symbolLength()) {
            throw new IllegalArgumentException(
                "symbol length must be between 0 and " + OrderBookSnapshotEncoder.symbolLength());
        }
        if (snapshot.getPricePrecision() < 0) {
            throw new IllegalArgumentException("price precision cannot be negative");
        }
        if (snapshot.getSentTimestamp() < 0) {
            throw new IllegalArgumentException("sent timestamp cannot be negative");
        }
        final int levelCount = snapshot.getOrderBookLength();
        if (levelCount < 0 || levelCount > 65_534) {
            throw new IllegalArgumentException("order book length must be between 0 and 65534");
        }
        requireLevelCapacity(snapshot, levelCount);
    }

    private static void requireTargetSymbolCapacity(final OrderBookSnapshot snapshot) {
        if (snapshot == null) {
            throw new NullPointerException("snapshot");
        }
        if (snapshot.getSymbol() == null ||
            snapshot.getSymbol().length < OrderBookSnapshotEncoder.symbolLength()) {
            throw new IllegalArgumentException(
                "symbol array capacity must be at least " + OrderBookSnapshotEncoder.symbolLength());
        }
    }

    private static void requireLevelCapacity(final OrderBookSnapshot snapshot, final int required) {
        if (snapshot.getBids() == null || snapshot.getBids().length < required ||
            snapshot.getAsks() == null || snapshot.getAsks().length < required ||
            snapshot.getBidSizes() == null || snapshot.getBidSizes().length < required ||
            snapshot.getAskSizes() == null || snapshot.getAskSizes().length < required) {
            throw new IllegalArgumentException(
                "all order book arrays must have capacity for " + required + " levels");
        }
    }
}
