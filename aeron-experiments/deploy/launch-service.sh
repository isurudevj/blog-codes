#!/usr/bin/env bash

set -euo pipefail

readonly APPS_DIR="/opt/apps"
readonly DEPLOY_DIR="${APPS_DIR}/deploy"
readonly CPU_AFFINITY_FILE="${DEPLOY_DIR}/env_files/cpu_affinity.csv"

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <service-name>_<instance-id>" >&2
    exit 64
fi

readonly app_instance="$1"

if [[ ! "${app_instance}" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*_[0-9]+$ ]]; then
    echo "Invalid application instance: ${app_instance}" >&2
    exit 64
fi

readonly service_name="${app_instance%_*}"
readonly jar_file="${APPS_DIR}/${service_name}.jar"
readonly java_opts="${JAVA_OPTS:--Xms2048m -Xmx2048m}"

if [[ ! -r "${jar_file}" ]]; then
    echo "Application JAR is not readable: ${jar_file}" >&2
    exit 66
fi

if [[ ! -r "${CPU_AFFINITY_FILE}" ]]; then
    echo "CPU affinity file is not readable: ${CPU_AFFINITY_FILE}" >&2
    exit 66
fi

cpu_affinity_mask="$(awk -F',' -v target="${app_instance}" '
    NR == 1 { next }
    {
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", $1)
        gsub(/^[[:space:]]+|[[:space:]\r]+$/, "", $2)
        if ($1 == target) {
            print $2
            exit
        }
    }
' "${CPU_AFFINITY_FILE}")"

if [[ -z "${cpu_affinity_mask}" ]]; then
    echo "No CPU affinity mask configured for ${app_instance}" >&2
    exit 78
fi

if [[ ! "${cpu_affinity_mask}" =~ ^(0[xX])?[0-9a-fA-F]+(,(0[xX])?[0-9a-fA-F]+)*$ ]]; then
    echo "Invalid CPU affinity mask for ${app_instance}: ${cpu_affinity_mask}" >&2
    exit 78
fi

read -r -a java_opts_args <<< "${java_opts}"

echo "Starting ${app_instance}: jar=${jar_file}, cpu_affinity_mask=${cpu_affinity_mask}, java_opts=${java_opts}"
exec taskset "${cpu_affinity_mask}" java "${java_opts_args[@]}" -jar "${jar_file}"
