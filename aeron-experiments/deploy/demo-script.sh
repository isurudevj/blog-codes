#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly APP_SCRIPT="${SCRIPT_DIR}/app.sh"
readonly STARTUP_DELAY_SECONDS=2

readonly -a SERVICES=(
    "media-driver 1 Media Driver instance 1"
    "media-driver 2 Media Driver instance 2"
    "aeron-archive 1 Aeron Archive instance 1"
    "aeron-archive 2 Aeron Archive instance 2"
    "artio-fix-engine 1 Artio FIX Engine instance 1"
    "artio-fix-engine 2 Artio FIX Engine instance 2"
    "price-distributor 1 Price Distributor"
    "pricing-engine 1 Pricing Engine"
    "artio-price-client 2 Artio Price Client instance 2"
    "monitoring-service 1 Monitoring Service"
)

if [[ ! -x "${APP_SCRIPT}" ]]; then
    echo "❌ Service launcher is not executable: ${APP_SCRIPT}" >&2
    exit 66
fi

echo "🎬 Starting the Aeron demo services in dependency order..."

for ((index = 0; index < ${#SERVICES[@]}; index++)); do
    read -r service_name instance_id display_name <<< "${SERVICES[index]}"

    printf '\n🚀 Starting %s (%s_%s)...\n' \
        "${display_name}" "${service_name}" "${instance_id}"
    "${APP_SCRIPT}" start "${service_name}" "${instance_id}"
    printf '✅ %s is running.\n' "${display_name}"

    if ((index < ${#SERVICES[@]} - 1)); then
        printf '⏳ Waiting %d seconds for the service to settle...\n' \
            "${STARTUP_DELAY_SECONDS}"
        sleep "${STARTUP_DELAY_SECONDS}"
    fi
done

printf '\n🎉 All Aeron demo services started successfully!\n'
