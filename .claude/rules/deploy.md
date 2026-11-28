---
paths:
  - "deploy/**"
  - "docker-compose.yml"
  - "Dockerfile"
  - "start_docker.sh"
  - "stop_docker.sh"
  - "src/main/resources/db/migration/**"
  - "docs/deploy-plan.md"
---

# Server deploy

Loaded when you touch the deploy scripts, the compose file, the `Dockerfile`, the local Docker scripts or the Flyway migrations. Usage and recovery are in `deploy/README.md`; the design record is `docs/deploy-plan.md` (the script wins where they differ).

## How it works

`deploy/deploy.sh` (bash) and `deploy/deploy.ps1` (Windows PowerShell 5.1 / 7) build the jar and stream one tar.gz over one password-only ssh connection into `DIR/.incoming-TAG`. Everything else is `deploy/deploy-remote.sh` (POSIX `sh`, shipped in every upload and copied into `DIR` for `--rollback`), which runs detached from the ssh session: lock, prechecks, adopt the compose project, compare the compose file, build the image while the old version serves, install the files, start `mariadb`, dump the database if a migration is pending, swap the app container (the only downtime), wait for health, roll back automatically unless the Flyway history changed, prune images.

## What the deploy reads from the rest of the repo

A change to any of these either needs a matching change in `deploy-remote.sh` or passes a deploy without doing what you expect. Most fail silently.

- **Services.** Only `mariadb` (`up -d --no-recreate`) and `server` (`up -d --no-deps --no-build`) are ever started. A new compose service is not started by a deploy.
- **Names.** The script finds containers by `container_name` `serial-ports-server` and `mariadb`, writes `services.server.image` into `docker-compose.override.yml`, tags images as `serial-protocol:<version>-<yyyyMMddHHmmss>`, and refuses to run when the `db-data` / `server-logs` volumes or the `backend` network carry another compose project label. Renaming any of them needs the constants at the top of `deploy-remote.sh` and `project()` changed too.
- **Health.** Success is the compose healthcheck of `server` reporting `healthy` within 180 s. A container without a healthcheck counts as healthy at once, so removing the healthcheck removes the check that triggers the automatic rollback.
- **Migrations.** The client lists `BOOT-INF/classes/db/migration/*` from the jar into `MIGRATIONS`; the server compares the top-level `V<n>__*.sql` names with `flyway_schema_history` (`success = 1`) and dumps the database only when one is missing. A migration in a subfolder or an `R__` script runs without a dump. The automatic rollback is skipped when the row count of `flyway_schema_history` changed, because the old jar does not pass `ddl-auto=validate` on a migrated schema.
- **Build context.** The image is built on the server from `Dockerfile`, `target/serial-protocol-<version>.jar` and `cert/server.p12` only. A `COPY` of any other file fails the build (loudly: nothing is stopped). Exactly one `target/serial-protocol-*.jar` must exist, and its version may use only `0-9A-Za-z._-`.
- **Compose file.** Only `docker-compose.yml` goes up; there is no `.env` or `env_file`. A server copy that differs aborts the deploy unless `--update-compose` is given, so a compose change needs that flag once per server.
- **Devices.** The precheck reads short-syntax `- /dev/x:...` entries with a `sed` that also lives in `start_docker.sh`; keep the two identical. A listed device missing on the host aborts the deploy before anything changes.

## Rules

- Never `down -v`, `volume prune` or a recreate of `mariadb` in any script: `db-data` holds all business data and `server-logs` holds `tracking.log`, the only source for `/tracks` replay.
- Manual compose commands on the server need `-p "$(cat DIR/.project)"`; the folder name is not the project name when the stack was adopted.
- `deploy-remote.sh` must stay POSIX `sh` (the server's `sh` may be `dash`). CI runs only `sh -n`, `bash -n` and the PowerShell parser (`.github/workflows/ci.yml`, job `deploy-scripts`), which do not catch bashisms or logic errors.
- What has run against a real server and what has not is listed under Limitations in `deploy/README.md`. Update that list when you test or change a path.
