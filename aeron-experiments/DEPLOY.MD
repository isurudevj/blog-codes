# Deployment Guide

The deployment scripts build the application JARs, copy them to a Linux host,
and install a reusable systemd service. Application instances run from
`/opt/apps` and are identified using the following naming convention:

```text
<service-name>_<instance-id>
```

For example, `media-driver_1` runs `media-driver.jar` using the environment and
CPU-affinity configuration for instance `1`.

## Prerequisites

The local machine requires:

- Java 17 or newer
- SSH and SCP access to the deployment host
- A remote user that can run the installation commands with `sudo`

The remote Linux host requires:

- Java 17 or newer
- systemd
- `taskset`, normally provided by the `util-linux` package
- Enough dedicated CPU cores and memory for the configured applications

Deployment creates a system user and group named `aeron` when they do not
already exist. Application services run under this account rather than as
`root`.

By default, the scripts deploy to the SSH host alias `artio` and install files
under `/opt/apps`.

## Configure an application instance

Create an environment file in `deploy/env_files` named after the application
instance. For example:

```text
deploy/env_files/media-driver_1.env
```

The file can define JVM options and Aeron configuration:

```shell
JAVA_OPTS=-Xms2048m -Xmx2048m
AERON_TERM_BUFFER_SPARSE_FILE=false
AERON_THREADING_MODE=DEDICATED
AERON_CONDUCTOR_IDLE_STRATEGY=spin
AERON_RECEIVER_IDLE_STRATEGY=noop
AERON_SENDER_IDLE_STRATEGY=noop
```

`JAVA_OPTS` defaults to `-Xms2048m -Xmx2048m` when it is absent or empty.
Aeron environment variables are converted to equivalent system properties by
the application; for example, `AERON_THREADING_MODE` becomes
`aeron.threading.mode`.

Add the instance and its CPU-affinity mask to
`deploy/env_files/cpu_affinity.csv`:

```csv
app_instance,cpu_affinity_mask
media-driver_1,0xE
```

The `0xE` mask permits the process to run on CPUs 1, 2, and 3. Choose a mask
appropriate for the target host. Each configured application instance must
have exactly one matching entry.

## Deploy

Run the deployment script from the project root:

```shell
./deploy/deploy.sh
```

The script performs a clean Gradle build, packages the application JARs and
deployment configuration, transfers the bundle over SSH, and installs:

```text
/opt/apps/*.jar
/opt/apps/deploy/launch-service.sh
/opt/apps/deploy/app.sh
/opt/apps/deploy/env_files/*
/etc/systemd/system/aeron-app@.service
```

It then reloads the systemd configuration. Deployment does not automatically
start an application.

The SSH destination and artifact version can be changed with environment
variables:

```shell
DEPLOY_HOST=my-server \
DEPLOY_VERSION=1.0.0-SNAPSHOT \
./deploy/deploy.sh
```

`DEPLOY_HOST` may be a hostname or an alias configured in `~/.ssh/config`.
The deployment script also accepts `DEPLOY_APPS_DIR` and
`DEPLOY_SYSTEMD_DIR`, but the current systemd unit and launcher expect
`/opt/apps`. Update those files as well before deploying to another application
directory.

## Start an application

On the remote host, use `app.sh` with the service name and numeric instance ID:

```shell
/opt/apps/deploy/app.sh start media-driver 1
```

This starts `aeron-app@media-driver_1.service` and prints its systemd status.
The launcher reads `media-driver_1.env`, applies its JVM options, uses the CPU
mask from `cpu_affinity.csv`, and starts `/opt/apps/media-driver.jar`. The
`start` command also enables the unit so it starts automatically when the host
boots.

## Stop an application

Use the same service name and instance ID:

```shell
/opt/apps/deploy/app.sh stop media-driver 1
```

systemd sends `SIGTERM` to the process. The Media Driver handles this signal,
unblocks its shutdown barrier, and closes cleanly. The `stop` command also
disables the unit so it remains stopped after a host reboot.

## Restart applications

Restart one application instance with:

```shell
/opt/apps/deploy/app.sh restart media-driver 1
```

Restarting also enables the service so it starts automatically when the host
boots.

Restart every application instance that has an environment file under
`/opt/apps/deploy/env_files` with:

```shell
/opt/apps/deploy/app.sh restart-all
```

The command derives each systemd unit name from its `*.env` filename, enables
and restarts all matching units, and prints their statuses. A stopped
configured unit will be started by this command because `systemctl restart`
starts inactive units.

## Status and logs

Inspect the current service state using `app.sh`:

```shell
/opt/apps/deploy/app.sh status media-driver 1
```

The equivalent direct systemd command is:

```shell
sudo systemctl status aeron-app@media-driver_1.service
```

Follow its logs with:

```shell
sudo journalctl -u aeron-app@media-driver_1.service -f
```

After changing an installed environment file or CPU-affinity entry, restart the
application so the new configuration is loaded:

```shell
/opt/apps/deploy/app.sh restart media-driver 1
```
