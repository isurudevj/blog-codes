#!/usr/bin/env bash

set -euo pipefail

usage() {
    echo "Usage: $0 <start|stop> <service-name> <instance-id>" >&2
    exit 64
}

if [[ $# -ne 3 ]]; then
    usage
fi

readonly command="$1"
readonly service_name="$2"
readonly instance_id="$3"

if [[ "${command}" != "start" && "${command}" != "stop" ]]; then
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
sudo systemctl "${command}" "${unit_name}"

if [[ "${command}" == "start" ]]; then
    sudo systemctl --no-pager --full status "${unit_name}"
else
    echo "Stopped ${unit_name}."
fi
