package io.github.aeron_experiments.pricingengine;

import io.aeron.Publication;
import io.github.aeron_experiments.codec_utils.OrderBookSnapshot;
import io.github.aeron_experiments.codec_utils.OrderBookSnapshotCodec;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

final class PricingEngineAgent implements Agent {
    private static final int BOOK_LEVELS = 10;
    private static final int BUFFER_CAPACITY = 1024;

    private final Publication publication;
    private final OrderBookSnapshot snapshot = new OrderBookSnapshot();
    private final OrderBookSnapshotCodec codec = new OrderBookSnapshotCodec();
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(BUFFER_CAPACITY));

    PricingEngineAgent(final Publication publication) {
        this.publication = publication;
        initialiseSnapshot();
    }

    public String roleName() {
        return "pricing-engine";
    }

    public int doWork() {
        snapshot.setSentTimestamp(System.nanoTime());
        final int encodedLength = codec.encode(snapshot, buffer, 0);
        return publication.offer(buffer, 0, encodedLength) > 0 ? 1 : 0;
    }

    private void initialiseSnapshot() {
        final char[] symbol = new char[16];
        symbol[0] = 'A';
        symbol[1] = 'A';
        symbol[2] = 'P';
        symbol[3] = 'L';
        snapshot.setSymbol(symbol);
        snapshot.setSymbolLength((short)4);
        snapshot.setPricePrecision((short)4);
        snapshot.setBids(new long[BOOK_LEVELS]);
        snapshot.setAsks(new long[BOOK_LEVELS]);
        snapshot.setBidSizes(new long[BOOK_LEVELS]);
        snapshot.setAskSizes(new long[BOOK_LEVELS]);
        snapshot.setOrderBookLength(BOOK_LEVELS);

        for (int i = 0; i < BOOK_LEVELS; i++) {
            snapshot.getBids()[i] = 1_895_000L - i;
            snapshot.getBidSizes()[i] = 100L + i;
            snapshot.getAsks()[i] = 1_895_100L + i;
            snapshot.getAskSizes()[i] = 120L + i;
        }
    }
}
