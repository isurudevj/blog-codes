# Aeron Experiments

Multi-module Gradle project for Aeron experiments and monitoring examples.

## Modules

- `media-driver` - Media Driver application and monitoring experiments
- `common-lib` - Shared library for pricing and monitoring applications
- `pricing-engine` - Pricing publisher/engine application
- `pricing-client` - Pricing consumer/client application
- `monitoring-service` - Aeron metrics collection and monitoring service
- `docker-stacks` - Docker Compose and configuration for Grafana and Prometheus
- `protocol-definitions` - Golden-source FIX dictionaries and SBE schemas
- `aeron-archive-service` - Standalone Aeron Archive connected to the Media Driver
- `artio-fix-engine` - Artio FIX 4.4 acceptor with inbound/outbound archive logging
- `price-distributor` - Artio library distributing prices over FIX market-data sessions
- `qfj-price-client` - QuickFIX/J initiator requesting and receiving streaming prices

## Requirements

- Java 17 or newer

## Build

```shell
./gradlew build
```

Application JARs are created under each Java module's `build/libs/` directory.

## Run

```shell
java -jar media-driver/build/libs/media-driver-1.0.0-SNAPSHOT.jar
java -jar pricing-engine/build/libs/pricing-engine-1.0.0-SNAPSHOT.jar
java -jar pricing-client/build/libs/pricing-client-1.0.0-SNAPSHOT.jar
java -jar monitoring-service/build/libs/monitoring-service-1.0.0-SNAPSHOT.jar
```

During development, the application can also be run with:

```shell
./gradlew :media-driver:run
./gradlew :pricing-engine:run
./gradlew :pricing-client:run
./gradlew :monitoring-service:run
```

## FIX price-distribution example

The FIX example is split along Artio's engine/library boundary. Start the four
processes in this order (the existing Media Driver is also required):

```shell
./gradlew :media-driver:run
./gradlew :aeron-archive-service:run
./gradlew :artio-fix-engine:run
./gradlew :price-distributor:run
./gradlew :qfj-price-client:run
```

The QuickFIX/J client logs on as `QFJ_PRICE_CLIENT`, sends a FIX 4.4
`MarketDataRequest (V)` for `AAPL`, and receives a two-sided
`MarketDataSnapshotFullRefresh (W)`. Set `PRICE_SYMBOL` to request another
symbol. Artio records both inbound and outbound FIX streams in Aeron Archive;
its replay indexes and engine state are stored under `data/artio`, while archive
recordings are stored under `data/archive`.

Configuration is available through environment variables:

| Variable | Default | Used by |
| --- | --- | --- |
| `AERON_DIR` | Aeron's default directory | archive, engine, pricing service |
| `AERON_ARCHIVE_DIR` | `data/archive` | archive |
| `ARTIO_LOG_DIR` | `data/artio` | engine |
| `ARTIO_ARCHIVE_PRUNING_ENABLED` | `true` | engine |
| `ARTIO_LIBRARY_CHANNEL` | `aeron:ipc` | engine, pricing service |
| `FIX_ACCEPTOR_HOST` | `0.0.0.0` | engine |
| `FIX_ACCEPTOR_PORT` | `9876` | engine |
| `PRICE_SYMBOL` | `AAPL` | QuickFIX/J client |

The QuickFIX/J connection settings are in
`qfj-price-client/src/main/resources/quickfixj-client.cfg`. If the host or port
is changed on the engine, update the corresponding `SocketConnectHost` or
`SocketConnectPort` setting there.

The FIX engine runs a dedicated busy-spinning Agrona agent that continuously
prunes Aeron Archive segments older than the latest sequence index. It submits
the next asynchronous prune immediately after the previous one completes, so
eligible segments are reclaimed without waiting for an engine restart.
Current-sequence messages remain available for resend. Set
`ARTIO_ARCHIVE_PRUNING_ENABLED=false` to disable the agent.

The Pricing Engine publishes a reusable 10-level `OrderBookSnapshot` as fast as
the Aeron publication accepts it. The Pricing Client decodes each message into
one reusable object and reports messages per second plus average and maximum
one-way latency once per second. Both applications use busy-spinning Agrona
agents and stop through a `ShutdownSignalBarrier`.

Their default transport is:

```text
channel:   aeron:ipc
stream ID: 1001
```

Override it with `PRICING_CHANNEL` and `PRICING_STREAM_ID`. The supplied Linux
deployment pins `pricing-engine_1` to CPU 4 (`0x10`) and `pricing-client_1` to
CPU 5 (`0x20`). One-way latency uses `System.nanoTime()` and is meaningful when
both processes run on the same host, as in this experiment.

## High-performance Media Driver

The `media-driver` application runs Aeron's Java Media Driver using its
configuration properties. To select the low-latency profile, provide these
environment variables:

```shell
export AERON_TERM_BUFFER_SPARSE_FILE=false
export AERON_USE_WINDOWS_HIGH_RES_TIMER=true
export AERON_THREADING_MODE=DEDICATED
export AERON_CONDUCTOR_IDLE_STRATEGY=spin
export AERON_RECEIVER_IDLE_STRATEGY=noop
export AERON_SENDER_IDLE_STRATEGY=noop
```

This profile uses dedicated conductor, sender, and receiver threads; a busy-spin
conductor; non-idling network agents; and preallocated (non-sparse) term buffers.
It intentionally consumes three CPU cores continuously. Do not use it on a host
without at least three cores available exclusively to the driver.

Build and run the self-contained JAR:

```shell
./gradlew :media-driver:jar
java -jar media-driver/build/libs/media-driver-1.0.0-SNAPSHOT.jar
```

Optional Aeron property files can be supplied as arguments, and individual
settings can be overridden with JVM system properties:

```shell
java \
  -Daeron.dir=/dev/shm/aeron \
  -Daeron.print.configuration=true \
  -jar media-driver/build/libs/media-driver-1.0.0-SNAPSHOT.jar \
  /path/to/aeron.properties
```

For stable latency in production, place the Aeron directory on a memory-backed
filesystem such as `/dev/shm` on Linux and pin the `driver-conductor`, `sender`,
and `receiver` threads to isolated physical CPU cores. Network MTU, socket
buffers, and term-buffer sizes must be tuned for the deployment rather than
hard-coded because their optimal values depend on the link's bandwidth-delay
product and message profile.
