#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly JMH_JAR="${SCRIPT_DIR}/jmh.jar"
readonly java_opts="${JAVA_OPTS:--Xms2048m -Xmx2048m}"

if [[ ! -r "${JMH_JAR}" ]]; then
    echo "JMH executable JAR is not readable: ${JMH_JAR}" >&2
    exit 66
fi

read -r -a java_opts_args <<< "${java_opts}"

if [[ $# -eq 0 ]]; then
    set -- OrderBookSnapshotCodecBenchmark -prof gc
fi

echo "Running JMH: java_opts=${java_opts}, arguments=$*"
exec java \
    "${java_opts_args[@]}" \
    --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
    -jar "${JMH_JAR}" \
    "$@"
