#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

readonly REMOTE_HOST="${DEPLOY_HOST:-artio}"
readonly REMOTE_APPS_DIR="${DEPLOY_APPS_DIR:-/opt/apps}"
readonly REMOTE_SYSTEMD_DIR="${DEPLOY_SYSTEMD_DIR:-/etc/systemd/system}"
readonly VERSION="${DEPLOY_VERSION:-1.0.0-SNAPSHOT}"

readonly -a SERVICES=(
    media-driver
    pricing-engine
    pricing-client
    monitoring-service
)

local_stage="$(mktemp -d "${TMPDIR:-/tmp}/aeron-deploy.XXXXXX")"
archive_file="${local_stage}/aeron-apps.tar.gz"

cleanup() {
    rm -rf -- "${local_stage}"
}
trap cleanup EXIT

echo "Building Aeron applications..."
"${PROJECT_DIR}/gradlew" -p "${PROJECT_DIR}" clean build :common-lib:microbenchmarkDist

mkdir -p \
    "${local_stage}/bundle/apps" \
    "${local_stage}/bundle/deploy/env_files" \
    "${local_stage}/bundle/microbenchmark"

for service in "${SERVICES[@]}"; do
    source_jar="${PROJECT_DIR}/${service}/build/libs/${service}-${VERSION}.jar"
    if [[ ! -f "${source_jar}" ]]; then
        echo "Build artifact not found: ${source_jar}" >&2
        exit 66
    fi

    cp -- "${source_jar}" "${local_stage}/bundle/apps/${service}.jar"
done

install -m 0755 "${SCRIPT_DIR}/launch-service.sh" "${local_stage}/bundle/deploy/launch-service.sh"
install -m 0755 "${SCRIPT_DIR}/app.sh" "${local_stage}/bundle/deploy/app.sh"
install -m 0755 "${PROJECT_DIR}/common-lib/build/distributions/microbenchmark/microbenchmark.sh" \
    "${local_stage}/bundle/microbenchmark/microbenchmark.sh"
install -m 0644 "${PROJECT_DIR}/common-lib/build/distributions/microbenchmark/jmh.jar" \
    "${local_stage}/bundle/microbenchmark/jmh.jar"
install -m 0644 "${SCRIPT_DIR}/aeron-app@.service" "${local_stage}/bundle/deploy/aeron-app@.service"
install -m 0644 "${SCRIPT_DIR}/env_files/cpu_affinity.csv" \
    "${local_stage}/bundle/deploy/env_files/cpu_affinity.csv"

env_files=("${SCRIPT_DIR}/env_files/"*.env)
if [[ ! -e "${env_files[0]}" ]]; then
    echo "No instance environment files found in ${SCRIPT_DIR}/env_files" >&2
    exit 66
fi
install -m 0644 "${env_files[@]}" "${local_stage}/bundle/deploy/env_files/"

tar -C "${local_stage}/bundle" -czf "${archive_file}" .

echo "Creating deployment workspace on ${REMOTE_HOST}..."
remote_stage="$(ssh "${REMOTE_HOST}" mktemp -d /tmp/aeron-apps.XXXXXX)"

remote_cleanup() {
    ssh "${REMOTE_HOST}" rm -rf -- "${remote_stage}" >/dev/null 2>&1 || true
}
trap 'remote_cleanup; cleanup' EXIT

echo "Uploading application bundle to ${REMOTE_HOST}..."
scp "${archive_file}" "${REMOTE_HOST}:${remote_stage}/aeron-apps.tar.gz"

echo "Installing applications on ${REMOTE_HOST}..."
ssh "${REMOTE_HOST}" bash -s -- \
    "${remote_stage}" "${REMOTE_APPS_DIR}" "${REMOTE_SYSTEMD_DIR}" <<'REMOTE_SCRIPT'
set -euo pipefail

readonly stage="$1"
readonly apps_dir="$2"
readonly systemd_dir="$3"

mkdir -p "${stage}/bundle"
tar -C "${stage}/bundle" -xzf "${stage}/aeron-apps.tar.gz"

if ! getent group aeron >/dev/null; then
    sudo groupadd --system aeron
fi
if ! id --user aeron >/dev/null 2>&1; then
    sudo useradd --system --gid aeron --no-create-home --shell /usr/sbin/nologin aeron
fi

sudo install -d -m 0755 \
    "${apps_dir}" \
    "${apps_dir}/deploy" \
    "${apps_dir}/deploy/env_files" \
    "${apps_dir}/microbenchmark"
sudo install -m 0644 "${stage}/bundle/apps/"*.jar "${apps_dir}/"
sudo install -m 0755 "${stage}/bundle/deploy/launch-service.sh" "${apps_dir}/deploy/launch-service.sh"
sudo install -m 0755 "${stage}/bundle/deploy/app.sh" "${apps_dir}/deploy/app.sh"
sudo install -m 0755 "${stage}/bundle/microbenchmark/microbenchmark.sh" \
    "${apps_dir}/microbenchmark/microbenchmark.sh"
sudo install -m 0644 "${stage}/bundle/microbenchmark/jmh.jar" \
    "${apps_dir}/microbenchmark/jmh.jar"
sudo install -m 0644 "${stage}/bundle/deploy/env_files/cpu_affinity.csv" \
    "${apps_dir}/deploy/env_files/cpu_affinity.csv"
sudo install -m 0644 "${stage}/bundle/deploy/env_files/"*.env "${apps_dir}/deploy/env_files/"
sudo install -m 0644 "${stage}/bundle/deploy/aeron-app@.service" \
    "${systemd_dir}/aeron-app@.service"

sudo systemctl daemon-reload
REMOTE_SCRIPT

remote_cleanup
trap cleanup EXIT

echo "Deployment to ${REMOTE_HOST} completed successfully."
echo "Start an instance with: sudo systemctl enable --now aeron-app@media-driver_1"
