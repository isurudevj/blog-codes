# Market Data SBE Schema

`src/main/resources/market-data.xml` defines an `OrderBookSnapshot` message.
The symbol belongs to the snapshot and is encoded once outside the repeating
order-book group. Each group entry represents the bid and ask at the same book
depth:

```text
OrderBookSnapshot
├── symbol: char[16]
├── pricePrecision: int16
└── orderBookLevels[]
    ├── bidPrice: int64
    ├── bidQuantity: int64
    ├── askPrice: int64
    └── askQuantity: int64
```

Prices are encoded directly as `int64` fixed-point integers. The root-level
`pricePrecision` field specifies the number of implied decimal places shared by
every price in the snapshot. For example, when `pricePrecision` is `4`, an
encoded price of `1234567` represents `123.4567`. Quantities are non-negative
integers expressed in the instrument's minimum quantity unit. Integer
representations avoid floating point rounding and keep every book level fixed
at 32 bytes.

The positive price range is `0.0000` through
`922337203685477.5807` when `pricePrecision` is `4`.

The fixed-length symbol is US-ASCII and NUL-padded when shorter than 16
characters. The order-book group is ordered from best prices to worst prices,
so the first entry is level one.

## Generate Java codecs

Generate the Java encoder and decoder flyweights with the official SBE tool:

```shell
./gradlew :common-lib:generateSbeCodecs
```

Generated sources are written to:

```text
common-lib/build/generated/sources/sbe/java
```

The directory is registered as a `common-lib` main Java source directory, and
`compileJava` automatically runs codec generation first. Generated files stay
under `build` and should not be committed; edit `market-data.xml` and regenerate
instead.

## Codec benchmarks

Run the JMH encode, decode, and combined round-trip benchmarks from the project
root:

```shell
./gradlew :common-lib:jmh
```

The default run benchmarks snapshots with 1, 10, and 20 levels and enables the
JMH GC profiler. A quick smoke run can override the JMH arguments:

```shell
./gradlew :common-lib:jmh \
  -PjmhArgs='OrderBookSnapshotCodecBenchmark -prof gc -wi 1 -i 1 -f 1 -w 100ms -r 100ms'
```
