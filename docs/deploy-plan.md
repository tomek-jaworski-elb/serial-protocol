# Deploy plan: Windows 11 / Linux workstation → Linux Docker server

Status: **implemented in `deploy/` (branch `deploy-scripts`); decisions in §10 (answered 2026-11-12).** Based on branch `master` (0911c6d).
Usage: [`deploy/README.md`](../deploy/README.md).

## 1. How the app runs today (master)

- `docker-compose.yml`: two services on the fixed network `backend`.
  - `server`: `build: .`, `container_name: serial-ports-server`, `8081:443`, `privileged`, `/dev/ttyS0-2`, `/dev/ttyACM0` and `/dev:/dev` mapped, volume `server-logs:/app/logs`, `restart: unless-stopped`, healthcheck `netcat -z localhost 443`, `depends_on: mariadb (healthy)`.
  - `mariadb`: `mariadb:11.8`, `container_name: mariadb`, volume `db-data`, `3306:3306` published, hard-coded passwords (`user/password`, `root`).
- Volumes and the network carry an explicit `name:`, so they are global per Docker host and do not depend on the compose project name. Containers keep the project label, which is taken from the folder name because the compose file sets no `name:`.
- `Dockerfile` does not build the app. It `COPY`s a prebuilt `target/serial-protocol-3.0.jar` and `cert/server.p12`. **`cert/` is gitignored**, so the server can never build from a git checkout alone.
- The build context is `.`, and there is no `.dockerignore`.
- `start_docker.sh` (on master) runs `up --build --force-recreate --always-recreate-deps`. That recreates MariaDB too, so a deploy must not use it. It also stopped the stack before building, so a failed build left no app. Since this branch it builds first, never recreates MariaDB and refuses a deploy-managed folder.
- Ports the app listens on: 443 (HTTPS), 80 and 8080 (redirect, not published), UDP 2222 (**not published**). Serial ports are opened in the container.
- State to keep: volume `db-data` (all business data, photos) and volume `server-logs` (`tracking.log` is the only source for `/tracks` replay).
- Schema: Flyway V1..V9 run at start-up with `ddl-auto=validate`. Migrations are one-way, so an old jar does not start on a migrated schema.

## 2. Why "always available" is not reachable without app changes

Two instances cannot run side by side:

- **Serial ports** are exclusive. A second instance only logs a failed open and reports itself "healthy" while it receives no frames.
- **UDP 2222**, host port **8081** and the fixed **`container_name`** are exclusive per host.
- **Flyway**: once the new version migrates, the old one runs on a schema it does not know.
- **`tracking.log`**: two JVMs appending to the same rolling file risk corrupting the replay API.
- HTTP sessions are held in memory. A restart logs everyone out whatever is done.

The best result without changing the app: **everything slow happens while the old version serves** (upload, image build, DB backup). The service is down only for the container swap, about **15–35 s** (estimated, measured in stage 0). §9 describes true zero downtime as a separate project.

## 3. Architecture

| File (new) | Role |
|---|---|
| `deploy/deploy.sh` | Linux client (bash). |
| `deploy/deploy.ps1` | Windows client (Windows PowerShell 5.1, built into Windows 11). |
| `deploy/deploy-remote.sh` | All server logic (POSIX `sh`). Shipped in the archive. |
| `deploy/deploy.conf.example` | Committed defaults. The real `deploy/deploy.conf` is gitignored. |
| `deploy/README.md` | Server prerequisites, usage, rollback and restore. |
| `.gitattributes` | `*.sh text eol=lf`, so a CRLF checkout on Windows does not break the server script. |
| `.gitignore` | `+ /deploy/deploy.conf`, `/deploy/.known_hosts` |

The clients stay thin: prompt, build, pack, **one** `ssh` call. Both systems have only OpenSSH (`ssh.exe`) and `tar` (`tar.exe`, bsdtar) by default, which is why there are two clients and no Python or extra library.
`Dockerfile` and `docker-compose.yml` stay as they are. `start_docker.sh` was fixed later (see §1).

## 4. Credentials

| What | How |
|---|---|
| SSH password | **The script never reads it.** `ssh` asks for it on the console itself, once, because there is one connection. It is not in a variable, argv, env, file, log or history. A `--password` option does not exist and is rejected. |
| SSH keys / agent | Not used: the client passes `-o PubkeyAuthentication=no -o PreferredAuthentications=password,keyboard-interactive`, so a password is always asked. |
| Username, IP, port, folder | Defaults may be in `deploy.conf`. These are not secrets. |
| Host key | `StrictHostKeyChecking=accept-new` with `UserKnownHostsFile=deploy/.known_hosts`. A changed key aborts the run. |
| sudo | Not needed: the login user has access to Docker. Without access, the precheck aborts. |
| App and DB passwords | Unchanged in v1. They are already in the repo. |

The script prints no command lines and has no `set -x` and no transcript.

## 5. Config file `deploy/deploy.conf`

```
# KEY=VALUE, '#' comments. There is no password key.
DEPLOY_HOST=192.168.2.4
DEPLOY_PORT=22
DEPLOY_USER=
DEPLOY_DIR=/opt/serial-protocol
```

- The file is parsed line by line against an allowlist of these keys and is never `source`d. Unknown keys get a warning and are ignored.
- Precedence: CLI flag (then that value is not asked for), then the answer at the prompt, whose default (Enter) comes from `deploy.conf`, then `deploy.conf.example`.
- `DEPLOY_DIR` must be absolute, match `^/[A-Za-z0-9._/-]+$` and contain no `..`. It needs at least two path components, so `/` and `/opt` are rejected. A non-empty folder without the `.project` marker (written by every deploy) is refused before anything is written into it. `~` is not accepted.

## 6. Client flow (the same in `deploy.sh` and `deploy.ps1`)

1. Read `deploy.conf` and ask for IP, user, port and folder (with defaults). After the build (step 2) it confirms with `Deploy <TAG> to user@host:/dir? [y/N]`.
2. Unless `--skip-build`, run `mvnw clean package -Dmaven.test.skip=true`. After `clean` there must be **exactly one** `target/serial-protocol-*.jar`, and the version is taken from its name. This avoids parsing `pom.xml`, whose first `<version>` is the Spring Boot parent's.
3. Stage the files in a temp folder outside the repo:
   - `build/` (the image context: `Dockerfile`, `cert/server.p12`, `target/serial-protocol-<V>.jar`)
   - `docker-compose.yml`
   - `deploy-remote.sh`, with CRs stripped
   - `MIGRATIONS` (the `db/migration` entries **of the jar**, not of the working tree, which may be a different commit)

   The version from the jar name must match `^[0-9A-Za-z._-]+$`; the tag is `<version>-<yyyyMMddHHmmss>`.

   A missing local `cert/server.p12` only warns; the server then uses its own copy and aborts if it has none.
4. `tar czf <tmp>.tgz -C <stage> .`, then a single call:
   ```
   ssh -p P -o ServerAliveInterval=15 -o StrictHostKeyChecking=accept-new \
       -o PubkeyAuthentication=no -o PreferredAuthentications=password,keyboard-interactive \
       -o UserKnownHostsFile=deploy/.known_hosts user@host \
       "sh -c 'I=DIR/.incoming-TAG; mkdir -p \"\$I\" && tar xzf - -C \"\$I\" && sh \"\$I/deploy-remote.sh\" start DIR \"\$I\"'" < tmp.tgz
   ```
   - `DIR` and `TAG` are validated literals, which is why the quoting is simple.
   - On Windows the redirection goes through `cmd /s /c "ssh ... < "tmp.tgz""`, because a PS 5.1 pipe corrupts binary data. Stage 0 confirms this.
   - Each deploy unpacks into its own `.incoming-TAG`, so two simultaneous deploys do not overwrite each other's files.
5. `deploy-remote.sh start` launches the real deploy **detached from the SSH session** (`setsid nohup … > DIR/deploy-logs/deploy-TAG.log`). It then follows that log (`tail -f --pid`) and returns its exit code. A dropped connection does not interrupt the swap and rollback. `deploy --attach` shows the log again (new password prompt).
6. Delete the temp archive.

Extra commands:
- `deploy --rollback`: return to `previous-tag`.
- `deploy --status`: show `docker compose ps` and the current tag.

## 7. Server sequence (`deploy-remote.sh`, POSIX sh, explicit status checks with no blanket `set -e` in the swap and rollback part)

1. **Lock.** `mkdir DIR/.lock` holding a PID. A lock counts as stale when its PID is dead or no longer a `deploy-remote.sh` (PID reuse after a reboot); it is reaped by an atomic `mv`. A lock without a PID younger than a minute means another run is just starting. The EXIT trap releases it; HUP is ignored, and INT/TERM are ignored from the swap on.
2. **Prechecks** (an abort here touches nothing):
   - `docker info` works. Otherwise: "user is not in the docker group".
   - `docker compose version` is v2.
   - Free space: at least 2 GB in `DIR` and in Docker's root dir (`docker info` `DockerRootDir`).
   - The `/dev/tty*` devices from the compose file exist. Without them `docker create` fails, so a missing device aborts the run.
3. **Project name.**
   - Read the `com.docker.compose.project` label from `serial-ports-server`, or from `mariadb` if the app container is absent.
   - Otherwise use `DIR/.project`, otherwise `serial-protocol`. Save the result to `.project`.
   - If the `backend` network or the `db-data`/`server-logs` volumes have a different project label, **abort** with an explanation.
   - Afterwards always use `docker compose -p P --project-directory DIR`.
4. **Build image while the old version serves.**
   - `docker build -t serial-protocol:TAG INC/build`. The context holds only the Dockerfile, the cert and the jar, so no backups or logs go in.
   - Failure: delete `INC` and exit. Nothing was stopped and no file in DIR changed.
5. **Install files only after a successful build.**
   - Compose file: on a first install it is copied. If the server copy differs from the shipped one, show the diff and **abort** (before the swap, nothing stopped) unless `--update-compose` is given.
   - `docker-compose.override.yml` with `services.server.image: serial-protocol:TAG`. Compose loads it automatically, so a manual `docker compose up` after a reboot or by hand runs the same image.
   - `cert/` gets mode 600, and `deploy-remote.sh` is copied into DIR for `--rollback`.
6. **MariaDB.** `up -d --no-recreate mariadb`, then wait for `healthy` (60 s).
   - On a first install this creates the volumes.
   - It never recreates an existing database container, so it also does not upgrade an old `mariadb` image. That is intended.
7. **Snapshot.**
   - Record the image ID of the running `serial-ports-server`, and tag it `serial-protocol:prev-TAG`. This makes rollback work even after a manual deploy with `start_docker.sh`.
   - `M0` is the number of rows in `flyway_schema_history`, failed ones included (0 if the table is missing).
   - `PENDING` is `MIGRATIONS` minus the versions applied successfully; a history that cannot be read counts as pending.
8. **DB backup, before the stop** (only when `PENDING` is non-empty, or with `--backup`).
   - Command: `docker exec mariadb sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb-dump -uroot --single-transaction certificates'`, written with `umask 077` to a temp file outside the image context.
   - The dump must exit with status 0 and the file must end with `-- Dump completed`; then it is compressed into `DIR/backups/`. Keep 5.
   - The password is read inside the container, so it never appears in argv on the host.
9. **Swap (the only downtime).**
   - If `serial-ports-server` exists: `docker stop -t 30` and `docker rm` (by name, because the old container may belong to a hand-started project).
   - Then `up -d --no-deps --no-build server`. Record the timestamps.
10. **Health.**
    - Poll every 2 s for up to 120 s (start-up includes Flyway and `ThumbnailBackfill`).
    - Success: `Health=healthy`.
    - Fast fail: the container is not running, or `State.Restarting` is set, or `RestartCount>0`. `restart: unless-stopped` makes a crashing container loop instead of stopping. A single `unhealthy` is not final.
11. **Failure.**
    - Run `docker logs --tail 80`, then read `M1`.
    - If `M1 == M0` and a previous image exists: stop and rm the container, point the override at `prev`, `up`, wait for health, exit 1 ("rolled back").
    - If `M1 != M0`: **no automatic rollback**, because the old jar does not pass `validate`. Record the failed tag as current and the old one as previous (so `rollback` after the restore goes to the right image), print the dump path and the restore command, exit 1.
12. **Success.**
    - Write `current-tag` and `previous-tag`.
    - Keep the last 3 `serial-protocol:*` tags plus the running and previous ones, then `docker image prune -f` (dangling layers only). Delete `INC`.
    - **Never `down -v` and never `volume prune`.**
13. **Report.** Print the tag, `https://<host>:8081/`, `docker compose ps` and the measured gap (from step 9 to healthy).

**First deploy over a stack started by hand:** its project name is adopted, and its image is kept as `prev`. Do this first deploy with someone watching.

## 8. Implementation stages (each one testable)

| # | Stage | Done when |
|---|---|---|
| 0 | **Spike, no code.** On the server or a VM: Docker and Compose v2 for the login user, `netcat` in the image (the package provides `nc`), measured start time, access to Docker Hub. On Windows 11: `ssh` prompts for the password with stdin redirected by `cmd <`, bytes reach the server intact (sha256), `tar.exe` flags work. | Findings recorded; the Windows transport chosen. |
| 1 | `deploy-remote.sh`, run by hand on a server with a manually unpacked archive. | First install, update, a broken jar with rollback, a missing device, a held or stale lock, a dropped SSH session during the swap. |
| 2 | `deploy.sh`, `deploy.conf.example`, `.gitignore`, `.gitattributes`. | Full deploy from Linux with one password prompt; `--password` rejected. |
| 3 | `deploy.ps1`. | Full deploy from Windows 11, from a path with a space; the jar's sha256 matches on the server. |
| 4 | Migrations and backup (the `MIGRATIONS` file, dump, the no-rollback message). | Test with a dummy `V10` on a copy of the database. |
| 5 | `deploy/README.md`, the command in `CLAUDE.md` and the `AGENTS.md` mirror (commands block). Optionally CI `bash -n` and the PowerShell parser. | Docs merged. |

## 9. Optional later: true zero downtime (separate project)

- A reverse proxy (nginx or caddy) owns 8081 and UDP 2222, with two app containers alternating (blue/green, separate log directories).
- It needs app changes:
  - no fixed `container_name`;
  - a flag to start without serial and UDP;
  - a management endpoint to hand over serial ports;
  - migrations written in the expand/contract style.
- Even then the serial handover takes about 1 s, and HTTP sessions are lost unless they are moved into the database.
- Worth it only if the 15–35 s from §2 cannot be accepted.

## 10. Decisions (answered 2026-11-12)

| # | Question | Decision |
|---|---|---|
| Q1 | Downtime | The 15–35 s gap is accepted. §9 is not planned. |
| Q2 | SSH login | Password only. Public-key auth is disabled in the client. |
| Q3 | Docker access | The login user has Docker access. No sudo. |
| Q4 | Server | A stack is running via `docker compose`, and its project name is adopted. The server has internet access, so the image is built on the server. `image` mode (`docker save`/`load`) is dropped. |
| Q5 | Frames | Serial. A missing `/dev/tty*` aborts the run. Ports are unchanged (UDP 2222 stays unpublished). |
| Q6 | Compose file | Diff, then abort; `--update-compose` overwrites. |
| Q7 | DB backup | A dump only when migrations are pending, keeping 5. After a migration, rollback is a manual restore. |
| Q8 | App/DB passwords, `3306:3306` | Default: unchanged in v1, a separate task. |
| Q9 | Operator tools | Default: JDK 25 and the Maven wrapper locally. `--skip-build` uses an existing jar. |
