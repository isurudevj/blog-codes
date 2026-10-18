#!/usr/bin/env bash

set -euo pipefail

usage() {
    echo "Usage:" >&2
    echo "  $0 <start|stop|restart|status> <service-name> <instance-id>" >&2
    echo "  $0 restart-all" >&2
    exit 64
}

if [[ $# -eq 1 && "$1" == "restart-all" ]]; then
    readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
    readonly ENV_FILES_DIR="${SCRIPT_DIR}/env_files"

    shopt -s nullglob
    env_files=("${ENV_FILES_DIR}/"*.env)
    shopt -u nullglob

    if [[ ${#env_files[@]} -eq 0 ]]; then
        echo "No application environment files found in ${ENV_FILES_DIR}" >&2
        exit 66
    fi

    unit_names=()
    for env_file in "${env_files[@]}"; do
        app_instance="$(basename -- "${env_file}" .env)"
        if [[ ! "${app_instance}" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*_[0-9]+$ ]]; then
            echo "Invalid application instance environment file: ${env_file}" >&2
            exit 64
        fi
        unit_names+=("aeron-app@${app_instance}.service")
    done

    echo "Restarting all configured Aeron applications: ${unit_names[*]}"
    sudo systemctl enable "${unit_names[@]}"
    sudo systemctl restart "${unit_names[@]}"
    sudo systemctl --no-pager --full status "${unit_names[@]}"
    exit 0
fi

if [[ $# -ne 3 ]]; then
    usage
fi

readonly command="$1"
readonly service_name="$2"
readonly instance_id="$3"

if [[ "${command}" != "start" && "${command}" != "stop" && "${command}" != "restart" && "${command}" != "status" ]]; then
    echo "Unsupported command: ${command}" >&2
    usage
fi

if [[ ! "${service_name}" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*$ ]]; then
    echo "Invalid service name: ${service_name}" >&2
    exit 64
fi

if [[ ! "${instance_id}" =~ ^[0-9]+$ ]]; then
    echo "Invalid instance ID: ${instance_id}" >&2
    exit 64
fi

readonly unit_name="aeron-app@${service_name}_${instance_id}.service"

echo "Running '${command}' for ${unit_name}..."
case "${command}" in
    start)
        sudo systemctl enable --now "${unit_name}"
        sudo systemctl --no-pager --full status "${unit_name}"
        ;;
    stop)
        sudo systemctl disable --now "${unit_name}"
        echo "Stopped and disabled ${unit_name}."
        ;;
    restart)
        sudo systemctl enable "${unit_name}"
        sudo systemctl restart "${unit_name}"
        sudo systemctl --no-pager --full status "${unit_name}"
        ;;
    status)
        sudo systemctl --no-pager --full status "${unit_name}"
        ;;
esac
