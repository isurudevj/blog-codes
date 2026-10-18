package io.github.aeron_experiments.common.codecs;

import io.github.aeron_experiments.codec_utils.OrderBookSnapshot;
import io.github.aeron_experiments.codec_utils.OrderBookSnapshotCodec;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OrderBookSnapshotObjectCodecTest {
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
    private final OrderBookSnapshotCodec codec = new OrderBookSnapshotCodec();
    private final OrderBookSnapshot source = snapshotWithCapacity(4);
    private final OrderBookSnapshot target = snapshotWithCapacity(4);

    @Test
    void shouldEncodeObjectAndDecodeIntoSameTargetObject() {
        setSymbol(source, "AAPL");
        source.setPricePrecision((short)4);
        source.setOrderBookLength(2);
        source.getBids()[0] = 1_895_000;
        source.getBidSizes()[0] = 100;
        source.getAsks()[0] = 1_895_100;
        source.getAskSizes()[0] = 120;
        source.getBids()[1] = 1_894_900;
        source.getBidSizes()[1] = 250;
        source.getAsks()[1] = 1_895_200;
        source.getAskSizes()[1] = 300;

        assertEquals(94, codec.encode(source, buffer, 0));
        assertSame(target, codec.decode(buffer, 0, target));

        assertEquals("AAPL", new String(target.getSymbol(), 0, target.getSymbolLength()));
        assertEquals(4, target.getPricePrecision());
        assertEquals(2, target.getOrderBookLength());
        assertEquals(1_895_000, target.getBids()[0]);
        assertEquals(100, target.getBidSizes()[0]);
        assertEquals(1_895_100, target.getAsks()[0]);
        assertEquals(120, target.getAskSizes()[0]);
        assertEquals(1_894_900, target.getBids()[1]);
        assertEquals(300, target.getAskSizes()[1]);
    }

    @Test
    void shouldReuseCodecAndTargetForSubsequentMessages() {
        setSymbol(source, "BTCUSD");
        source.setPricePrecision((short)2);
        source.setOrderBookLength(1);
        source.getBids()[0] = 6_750_000;
        source.getBidSizes()[0] = 3;
        source.getAsks()[0] = 6_750_100;
        source.getAskSizes()[0] = 2;

        codec.encode(source, buffer, 128);
        final OrderBookSnapshot firstResult = codec.decode(buffer, 128, target);

        setSymbol(source, "EURUSD");
        source.setPricePrecision((short)4);
        source.getBids()[0] = 10_750;
        codec.encode(source, buffer, 128);
        final OrderBookSnapshot secondResult = codec.decode(buffer, 128, target);

        assertSame(firstResult, secondResult);
        assertSame(target, secondResult);
        assertEquals("EURUSD", new String(target.getSymbol(), 0, target.getSymbolLength()));
        assertEquals(10_750, target.getBids()[0]);
    }

    private static OrderBookSnapshot snapshotWithCapacity(final int capacity) {
        final OrderBookSnapshot snapshot = new OrderBookSnapshot();
        snapshot.setSymbol(new char[16]);
        snapshot.setBids(new long[capacity]);
        snapshot.setAsks(new long[capacity]);
        snapshot.setBidSizes(new long[capacity]);
        snapshot.setAskSizes(new long[capacity]);
        return snapshot;
    }

    private static void setSymbol(final OrderBookSnapshot snapshot, final String value) {
        value.getChars(0, value.length(), snapshot.getSymbol(), 0);
        snapshot.setSymbolLength((short)value.length());
    }
}
