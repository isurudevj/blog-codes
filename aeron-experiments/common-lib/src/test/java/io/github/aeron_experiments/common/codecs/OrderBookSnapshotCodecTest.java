package io.github.aeron_experiments.common.codecs;

import io.github.aeron_experiments.codec_utils.OrderBookSnapshotDecoder;
import io.github.aeron_experiments.codec_utils.OrderBookSnapshotEncoder;
import io.github.aeron_experiments.codec_utils.MessageHeaderDecoder;
import io.github.aeron_experiments.codec_utils.MessageHeaderEncoder;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBookSnapshotCodecTest {
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final OrderBookSnapshotEncoder encoder = new OrderBookSnapshotEncoder();
    private final OrderBookSnapshotDecoder decoder = new OrderBookSnapshotDecoder();

    @Test
    void shouldEncodeAndDecodeSnapshotWithoutCreatingPerLevelFlyweights() {
        final OrderBookSnapshotEncoder.OrderBookLevelsEncoder levelsEncoder = encoder
            .wrapAndApplyHeader(buffer, 0, headerEncoder)
            .symbol("AAPL")
            .pricePrecision((short)4)
            .orderBookLevelsCount(2);

        assertSame(levelsEncoder, levelsEncoder.next());
        levelsEncoder
            .bidPrice(1_895_000L)
            .bidQuantity(100)
            .askPrice(1_895_100L)
            .askQuantity(120);
        assertSame(levelsEncoder, levelsEncoder.next());
        levelsEncoder
            .bidPrice(1_894_900L)
            .bidQuantity(250)
            .askPrice(1_895_200L)
            .askQuantity(300);

        assertEquals(94, MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());

        decoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        assertEquals(0, decoder.actingVersion());
        assertEquals('A', decoder.symbol(0));
        assertEquals('L', decoder.symbol(3));
        assertEquals(0, decoder.symbol(4));
        assertEquals(4, decoder.pricePrecision());

        final OrderBookSnapshotDecoder.OrderBookLevelsDecoder levelsDecoder = decoder.orderBookLevels();
        assertEquals(2, levelsDecoder.count());
        assertTrue(levelsDecoder.hasNext());
        assertSame(levelsDecoder, levelsDecoder.next());
        assertEquals(1_895_000L, levelsDecoder.bidPrice());
        assertEquals(100, levelsDecoder.bidQuantity());
        assertEquals(1_895_100L, levelsDecoder.askPrice());
        assertEquals(120, levelsDecoder.askQuantity());

        assertSame(levelsDecoder, levelsDecoder.next());
        assertEquals(1_894_900L, levelsDecoder.bidPrice());
        assertEquals(250, levelsDecoder.bidQuantity());
        assertEquals(1_895_200L, levelsDecoder.askPrice());
        assertEquals(300, levelsDecoder.askQuantity());
        assertFalse(levelsDecoder.hasNext());
        assertEquals(encoder.encodedLength(), decoder.encodedLength());
    }

    @Test
    void shouldReuseCodecInstancesForAnotherMessage() {
        encodeSingleLevel("EURUSD", (short)4, 10_750L, 1_000, 10_751L, 900);
        decoder.wrapAndApplyHeader(buffer, 64, headerDecoder);
        final OrderBookSnapshotDecoder.OrderBookLevelsDecoder firstFlyweight = decoder.orderBookLevels();
        firstFlyweight.next();
        assertEquals(10_750L, firstFlyweight.bidPrice());

        encodeSingleLevel("BTCUSD", (short)2, 6_750_000L, 3, 6_750_100L, 2);
        decoder.wrapAndApplyHeader(buffer, 64, headerDecoder);
        final OrderBookSnapshotDecoder.OrderBookLevelsDecoder reusedFlyweight = decoder.orderBookLevels();
        reusedFlyweight.next();

        assertSame(firstFlyweight, reusedFlyweight);
        assertEquals(2, decoder.pricePrecision());
        assertEquals('B', decoder.symbol(0));
        assertEquals('D', decoder.symbol(5));
        assertEquals(0, decoder.symbol(6));
        assertEquals(6_750_000L, reusedFlyweight.bidPrice());
        assertEquals(3, reusedFlyweight.bidQuantity());
    }

    private void encodeSingleLevel(
        final String symbol,
        final short precision,
        final long bidPrice,
        final long bidQuantity,
        final long askPrice,
        final long askQuantity) {
        encoder
            .wrapAndApplyHeader(buffer, 64, headerEncoder)
            .symbol(symbol)
            .pricePrecision(precision)
            .orderBookLevelsCount(1)
            .next()
            .bidPrice(bidPrice)
            .bidQuantity(bidQuantity)
            .askPrice(askPrice)
            .askQuantity(askQuantity);
    }
}
