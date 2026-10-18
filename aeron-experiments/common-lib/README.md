# Common Library

## Reusable order-book object codec

`OrderBookSnapshotCodec` adapts reusable `OrderBookSnapshot` objects to the
generated SBE encoder and decoder. Allocate the buffer, codec, snapshot, and
snapshot arrays once during application setup:

```java
UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(4096));
OrderBookSnapshotCodec codec = new OrderBookSnapshotCodec();

OrderBookSnapshot outbound = new OrderBookSnapshot();
outbound.setSymbol(new char[16]);
outbound.setBids(new long[20]);
outbound.setAsks(new long[20]);
outbound.setBidSizes(new long[20]);
outbound.setAskSizes(new long[20]);

OrderBookSnapshot inbound = new OrderBookSnapshot();
inbound.setSymbol(new char[16]);
inbound.setBids(new long[20]);
inbound.setAsks(new long[20]);
inbound.setBidSizes(new long[20]);
inbound.setAskSizes(new long[20]);
```

Set the outbound values and encode directly into the reusable buffer:

```java
"AAPL".getChars(0, 4, outbound.getSymbol(), 0);
outbound.setSymbolLength((short)4);
outbound.setPricePrecision((short)4);
outbound.setOrderBookLength(1);
outbound.getBids()[0] = 1_895_000;
outbound.getBidSizes()[0] = 100;
outbound.getAsks()[0] = 1_895_100;
outbound.getAskSizes()[0] = 120;

int encodedLength = codec.encode(outbound, buffer, 0);
```

Decode into the existing target object:

```java
OrderBookSnapshot decoded = codec.decode(buffer, 0, inbound);
```

`decoded` and `inbound` are the same object. The codec also reuses the same
generated message-header, message, and repeating-group flyweights on every
call. Decoding throws if the supplied arrays are too small rather than
allocating replacements on the message-processing path.

## JMH benchmarks

Run the object codec encode, decode, and round-trip benchmarks with:

```shell
./gradlew :common-lib:jmh
```

The benchmark measures snapshots containing 1, 10, and 20 order-book levels
and enables JMH's GC profiler by default. For a short validation run:

```shell
./gradlew :common-lib:jmh \
  -PjmhArgs='OrderBookSnapshotCodecBenchmark -prof gc -wi 1 -i 1 -f 1 -w 100ms -r 100ms'
```

Benchmark setup allocates the direct buffer, source and target snapshots,
arrays, and codec before measurement. Each measured invocation reuses those
objects.
