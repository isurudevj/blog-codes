# Aeron Experiments

Multi-module Gradle project for Aeron experiments and monitoring examples.

## Modules

- `media-driver` - Media Driver application and monitoring experiments
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
