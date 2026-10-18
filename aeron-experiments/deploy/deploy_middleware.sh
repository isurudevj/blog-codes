#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly REMOTE_HOST="${DEPLOY_HOST:-artio}"
readonly PROMETHEUS_VERSION="${PROMETHEUS_VERSION:-3.15.0}"
readonly GRAFANA_VERSION="${GRAFANA_VERSION:-13.2.2}"
readonly PROMETHEUS_URL="${PROMETHEUS_URL:-https://github.com/prometheus/prometheus/releases/download/v${PROMETHEUS_VERSION}/prometheus-${PROMETHEUS_VERSION}.linux-amd64.tar.gz}"
readonly PROMETHEUS_SHA256="${PROMETHEUS_SHA256:-2a542df32eac02ee17b9d844fb2aa1de00dafa5476579ba8a3ba862e9d572ea0}"
readonly GRAFANA_URL="${GRAFANA_URL:-https://dl.grafana.com/grafana/release/13.2.2/grafana_13.2.2_34846740809_linux_amd64.tar.gz}"
readonly GRAFANA_SHA256="${GRAFANA_SHA256:-9662c838a09824fdb072e5f6fbdd45b62cf541b20f3d609ea5011e6e5f544c8f}"

if [[ ! "${PROMETHEUS_VERSION}" =~ ^[0-9][0-9A-Za-z.-]*$ ||
      ! "${GRAFANA_VERSION}" =~ ^[0-9][0-9A-Za-z.-]*$ ]]; then
    echo "Middleware versions may contain only letters, numbers, dots, and hyphens." >&2
    exit 64
fi

if [[ ! "${PROMETHEUS_SHA256}" =~ ^[0-9a-fA-F]{64}$ ||
      ! "${GRAFANA_SHA256}" =~ ^[0-9a-fA-F]{64}$ ]]; then
    echo "Middleware SHA-256 values must contain exactly 64 hexadecimal characters." >&2
    exit 64
fi

local_stage="$(mktemp -d "${TMPDIR:-/tmp}/aeron-middleware.XXXXXX")"
archive_file="${local_stage}/middleware-config.tar.gz"

cleanup() {
    rm -rf -- "${local_stage}"
}
trap cleanup EXIT

tar -C "${SCRIPT_DIR}/middleware" -czf "${archive_file}" .

echo "Creating middleware deployment workspace on ${REMOTE_HOST}..."
remote_stage="$(ssh "${REMOTE_HOST}" mktemp -d /tmp/aeron-middleware.XXXXXX)"

remote_cleanup() {
    ssh "${REMOTE_HOST}" rm -rf -- "${remote_stage}" >/dev/null 2>&1 || true
}
trap 'remote_cleanup; cleanup' EXIT

echo "Uploading Prometheus and Grafana configuration..."
scp "${archive_file}" "${REMOTE_HOST}:${remote_stage}/middleware-config.tar.gz"

echo "Installing native Prometheus ${PROMETHEUS_VERSION} and Grafana ${GRAFANA_VERSION}..."
ssh "${REMOTE_HOST}" bash -s -- \
    "${remote_stage}" \
    "${PROMETHEUS_VERSION}" \
    "${PROMETHEUS_URL}" \
    "${PROMETHEUS_SHA256}" \
    "${GRAFANA_VERSION}" \
    "${GRAFANA_URL}" \
    "${GRAFANA_SHA256}" <<'REMOTE_SCRIPT'
set -euo pipefail

readonly stage="$1"
readonly prometheus_version="$2"
readonly prometheus_url="$3"
readonly prometheus_sha256="$4"
readonly grafana_version="$5"
readonly grafana_url="$6"
readonly grafana_sha256="$7"
readonly install_root="/opt/middleware"

if [[ "$(uname -s)" != "Linux" || "$(uname -m)" != "x86_64" ]]; then
    echo "Default middleware artifacts require Linux x86_64; override URLs and checksums for this host." >&2
    exit 78
fi

for command_name in curl sha256sum tar sudo; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
        echo "Required command is unavailable: ${command_name}" >&2
        exit 69
    fi
done

mkdir -p "${stage}/config"
tar -C "${stage}/config" -xzf "${stage}/middleware-config.tar.gz"

prometheus_archive="${stage}/prometheus.tar.gz"
grafana_archive="${stage}/grafana.tar.gz"
curl --fail --location --retry 3 --output "${prometheus_archive}" "${prometheus_url}"
curl --fail --location --retry 3 --output "${grafana_archive}" "${grafana_url}"
echo "${prometheus_sha256}  ${prometheus_archive}" | sha256sum --check --status
echo "${grafana_sha256}  ${grafana_archive}" | sha256sum --check --status

mkdir -p "${stage}/prometheus-extract" "${stage}/grafana-extract"
tar -C "${stage}/prometheus-extract" -xzf "${prometheus_archive}"
tar -C "${stage}/grafana-extract" -xzf "${grafana_archive}"
prometheus_source="$(find "${stage}/prometheus-extract" -mindepth 1 -maxdepth 1 -type d -print -quit)"
grafana_source="$(find "${stage}/grafana-extract" -mindepth 1 -maxdepth 1 -type d -print -quit)"
if [[ -z "${prometheus_source}" || -z "${grafana_source}" ]]; then
    echo "Downloaded archive did not contain the expected top-level directory." >&2
    exit 65
fi

if ! getent group prometheus >/dev/null; then
    sudo groupadd --system prometheus
fi
if ! id --user prometheus >/dev/null 2>&1; then
    sudo useradd --system --gid prometheus --home-dir /var/lib/prometheus --shell /usr/sbin/nologin prometheus
fi
if ! getent group grafana >/dev/null; then
    sudo groupadd --system grafana
fi
if ! id --user grafana >/dev/null 2>&1; then
    sudo useradd --system --gid grafana --home-dir /var/lib/grafana --shell /usr/sbin/nologin grafana
fi

sudo install -d -m 0755 "${install_root}"
sudo cp -a "${prometheus_source}" "${install_root}/prometheus-${prometheus_version}.new"
sudo cp -a "${grafana_source}" "${install_root}/grafana-${grafana_version}.new"
sudo rm -rf -- "${install_root}/prometheus-${prometheus_version}" "${install_root}/grafana-${grafana_version}"
sudo mv "${install_root}/prometheus-${prometheus_version}.new" "${install_root}/prometheus-${prometheus_version}"
sudo mv "${install_root}/grafana-${grafana_version}.new" "${install_root}/grafana-${grafana_version}"
sudo ln -sfn "${install_root}/prometheus-${prometheus_version}" "${install_root}/prometheus"
sudo ln -sfn "${install_root}/grafana-${grafana_version}" "${install_root}/grafana"

# Archives extracted below /tmp inherit user_tmp_t on SELinux hosts, which
# prevents systemd services from executing the copied binaries.
if command -v restorecon >/dev/null 2>&1; then
    sudo restorecon -RF "${install_root}"
fi

# Grafana 13 updates bundled datasource plugins in place. The service account
# therefore needs write access to this directory or the updater can stop the
# Prometheus plugin without being able to install its replacement.
sudo chown -R grafana:grafana "${install_root}/grafana-${grafana_version}/data/plugins-bundled"

sudo install -d -o prometheus -g prometheus -m 0755 /etc/prometheus /var/lib/prometheus
sudo install -m 0644 "${stage}/config/prometheus.yml" /etc/prometheus/prometheus.yml
sudo chown -R prometheus:prometheus /var/lib/prometheus

sudo install -d -o grafana -g grafana -m 0755 \
    /etc/grafana/provisioning/datasources \
    /etc/grafana/provisioning/dashboards \
    /var/lib/grafana/dashboards \
    /var/lib/grafana/plugins \
    /var/log/grafana
sudo install -m 0644 "${stage}/config/grafana.ini" /etc/grafana/grafana.ini
sudo install -m 0644 "${stage}/config/provisioning/datasources/prometheus.yml" \
    /etc/grafana/provisioning/datasources/prometheus.yml
sudo install -m 0644 "${stage}/config/provisioning/dashboards/aeron.yml" \
    /etc/grafana/provisioning/dashboards/aeron.yml
sudo install -m 0644 "${stage}/config/dashboards/aeron-ipc.json" \
    /var/lib/grafana/dashboards/aeron-ipc.json
sudo chown -R grafana:grafana /var/lib/grafana /var/log/grafana

sudo install -m 0644 "${stage}/config/prometheus.service" /etc/systemd/system/prometheus.service
sudo install -m 0644 "${stage}/config/grafana.service" /etc/systemd/system/grafana.service
sudo systemctl daemon-reload
sudo systemctl enable --now prometheus.service grafana.service
sudo systemctl restart prometheus.service grafana.service

if command -v firewall-cmd >/dev/null 2>&1 && systemctl is-active --quiet firewalld; then
    sudo firewall-cmd --permanent --zone=public --add-port=3000/tcp
    sudo firewall-cmd --reload
fi

sudo systemctl --no-pager --full status prometheus.service grafana.service
REMOTE_SCRIPT

remote_cleanup
trap cleanup EXIT

echo "Middleware deployment to ${REMOTE_HOST} completed successfully."
echo "Grafana is available at: http://${REMOTE_HOST}:3000"
echo "Allow inbound TCP/3000 from your trusted network if the host firewall blocks it."
