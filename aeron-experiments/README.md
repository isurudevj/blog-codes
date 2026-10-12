# Aeron Experiments

Multi-module Gradle project for Aeron experiments and monitoring examples.

## Modules

- `media-driver` - Media Driver application and monitoring experiments
- `common-lib` - Shared library for pricing and monitoring applications
- `pricing-engine` - Pricing publisher/engine application
- `pricing-client` - Pricing consumer/client application
- `monitoring-service` - Aeron metrics collection and monitoring service
- `docker-stacks` - Docker Compose and configuration for Grafana and Prometheus
- `sbe-files` - SBE message schemas and related files

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
