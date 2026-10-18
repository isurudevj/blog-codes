package io.github.aeron_experiments.common.jmh;

import io.github.aeron_experiments.codec_utils.OrderBookSnapshot;
import io.github.aeron_experiments.codec_utils.OrderBookSnapshotCodec;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class OrderBookSnapshotCodecBenchmark {
    private static final int MAX_LEVELS = 20;

    @Param({"1", "10", "20"})
    private int levelCount;

    private UnsafeBuffer buffer;
    private OrderBookSnapshotCodec codec;
    private OrderBookSnapshot source;
    private OrderBookSnapshot target;

    @Setup(Level.Trial)
    public void setup() {
        buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(4096));
        codec = new OrderBookSnapshotCodec();
        source = snapshotWithCapacity(MAX_LEVELS);
        target = snapshotWithCapacity(MAX_LEVELS);

        source.getSymbol()[0] = 'A';
        source.getSymbol()[1] = 'A';
        source.getSymbol()[2] = 'P';
        source.getSymbol()[3] = 'L';
        source.setSymbolLength((short)4);
        source.setPricePrecision((short)4);
        source.setSentTimestamp(1_760_768_739_123_456_789L);

        for (int i = 0; i < MAX_LEVELS; i++) {
            source.getBids()[i] = 1_895_000L - i;
            source.getBidSizes()[i] = 100L + i;
            source.getAsks()[i] = 1_895_100L + i;
            source.getAskSizes()[i] = 120L + i;
        }
    }

    @Setup(Level.Iteration)
    public void prepareEncodedSnapshot() {
        source.setOrderBookLength(levelCount);
        codec.encode(source, buffer, 0);
    }

    @Benchmark
    public int encode() {
        return codec.encode(source, buffer, 0);
    }

    @Benchmark
    public long decode() {
        codec.decode(buffer, 0, target);
        return target.getBids()[0] + target.getAsks()[levelCount - 1] + target.getOrderBookLength();
    }

    @Benchmark
    public long encodeAndDecode() {
        codec.encode(source, buffer, 0);
        codec.decode(buffer, 0, target);
        return target.getBids()[0] + target.getAsks()[levelCount - 1] + target.getOrderBookLength();
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
}
