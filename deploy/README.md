# Deploying to the Linux server

`deploy.sh` (Linux) and `deploy.ps1` (Windows 11) deploy the app to a Linux server that runs Docker.
`deploy-remote.sh` is the server side and goes up with every deploy. The design and its decisions are in
[`docs/deploy-plan.md`](../docs/deploy-plan.md).

```
deploy/deploy.sh                                            # Linux
powershell -ExecutionPolicy Bypass -File deploy\deploy.ps1  # Windows 11
deploy/deploy.sh --help   |   deploy\deploy.ps1 -Help       # options
```

The script asks for the server, ssh port, user and folder, unless given as options. Pressing Enter takes the default from `deploy/deploy.conf`; copy
`deploy.conf.example` to start one (the copy is gitignored). Then it builds the jar and asks for confirmation.
**ssh asks for the password once.** The script never reads, passes or stores it, and public-key login is switched off.

## What happens

1. The client builds `target/serial-protocol-<version>.jar` (`--skip-build` uses the existing one). It packs
   the jar, `Dockerfile`, `docker-compose.yml`, `cert/server.p12`, the migration file names and `deploy-remote.sh`,
   and streams the archive over one ssh connection into `DIR/.incoming-<tag>`.
2. The server runs the rest **detached from the ssh session**. A dropped connection does not stop it, and
   `--attach` shows its log again. The steps:
   - It refuses a non-empty folder that no deploy created. Then it takes a lock (`DIR/.lock`) and runs the prechecks:
     - Docker and Compose v2 are usable;
     - at least 2 GB is free in `DIR` and in Docker's data root;
     - every `/dev/...` device in the compose file exists.
   - It adopts the compose project of the running containers.
   - It builds the image `serial-protocol:<version>-<timestamp>` while the old version keeps serving.
   - If the server's `docker-compose.yml` differs from the uploaded one, it shows the diff and stops before any change,
     unless `--update-compose` was given.
   - It starts `mariadb` if it is not running. The database container is never recreated.
   - If the jar has a migration the database has not applied (or with `--backup`), it dumps the database to
     `DIR/backups/db-<tag>.sql.gz` (the last 5 are kept).
   - It replaces the `serial-ports-server` container and waits for it to become healthy. **This is the only downtime,
     about 15–35 s.** Users are logged out, and serial frames sent during the swap are lost.
   - If the new container does not become healthy, it switches back to the previous image automatically, unless
     a migration has already run (see below).
   - It keeps the 3 newest images plus the previous one.

> **Warning: do not run `start_docker.sh` (or `docker compose up --build`) in `DIR`.** The folder holds no
> `Dockerfile` and no jar, so a rebuild cannot work. The old `start_docker.sh` stopped the stack *before* building
> and left the server without the app; the current one refuses to run in a deploy-managed folder. On the server use
> `deploy.sh --status` or `--rollback`. To start or stop the stack by hand, run compose in `DIR` with the adopted
> project name: `docker compose -p "$(cat .project)" up -d` (the override pins the deployed image) or `... stop`.
> A plain `docker compose` takes the folder name as the project; when the stack was first started from another
> folder that name differs, and `up` fails (the containers already belong to the adopted project) while `down` stops nothing.
> `docker compose -p "$(cat .project)" down` is safe: it never removes the `db-data` volume. Never add `-v`.
> After a reboot both containers come back on their own (`restart: unless-stopped`).

Server layout (`DIR`): `docker-compose.yml`, `docker-compose.override.yml` (pins the image tag, so a manual `up`
runs the deployed version), `cert/`, `current-tag`, `previous-tag`, `.project` (the compose project name),
`deploy-remote.sh`, `deploy-logs/`, `backups/`. Volumes `db-data` and `server-logs` are never touched.

## First deploy to a server that already runs the stack

Choose a **new, empty folder**, e.g. `/opt/serial-protocol`. The deploy refuses a non-empty folder that no deploy
created, so the folder the stack was started from by hand (with its own `Dockerfile`, jar and `start_docker.sh`) is
not accepted. That folder stays untouched as a fallback.

The deploy adopts the running stack: its compose project, the `mariadb` container and the `db-data`/`server-logs`
volumes. Only the app container is replaced. The old folder's `docker-compose.yml` is not compared with the
uploaded one, so check that the repo's compose file carries this server's settings before the first run
(`rs.comports`, `ip.db-client`, the `/dev` devices).

## Server prerequisites

- Linux with Docker Engine and the Compose v2 plugin, and internet access to Docker Hub and the Ubuntu mirrors
  (the image is built on the server).
- The ssh user can run `docker` without sudo (member of the `docker` group, which is root-equivalent).
  Its login shell must be POSIX (`bash`, `sh`, `dash` or `zsh`).
- The ssh user can create `DIR` (e.g. `sudo install -d -o <user> /opt/serial-protocol` once).
- Password login is enabled in `sshd` (`PasswordAuthentication yes` or keyboard-interactive).

The client needs JDK 25 (for the Maven build), plus `ssh` and `tar`. Windows 11 has both built in.

The first connection stores the server's host key in `deploy/.known_hosts` (gitignored). A changed key aborts the run.

## Rollback

`--rollback` switches the app back to `previous-tag`. It swaps only the image; `docker-compose.yml` stays as
deployed. After a deploy that ran a migration, the old version no longer starts on the database. Restore the dump
the deploy printed first. The database is dropped and created empty before the load, because the dump only replaces
the tables it contains, and a table made by the failed migration would survive and break the next deploy:

```
docker stop serial-ports-server
docker exec mariadb sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb -uroot -e "DROP DATABASE $MARIADB_DATABASE; CREATE DATABASE $MARIADB_DATABASE"'
gunzip -c DIR/backups/db-<tag>.sql.gz | docker exec -i mariadb sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb -uroot "$MARIADB_DATABASE"'
sh DIR/deploy-remote.sh rollback DIR
```

The restore returns the database to the moment of the dump: anything written after it is lost.

If the rollback itself fails and the image that ran before still exists, the script switches back to it and says so.
Running `--rollback` again switches back to the newer image, because each rollback swaps `current-tag` and
`previous-tag`.

## Limitations

- Every deploy has a gap of about 15–35 s. Users are logged out, and serial frames sent during the swap are lost.
  A new version that never becomes healthy keeps the app down for up to about 5 minutes (up to 30 s stop,
  180 s health wait, then the rollback swap).
  True zero downtime needs app changes (see §9 of the plan).
- After a deploy that ran a migration there is no automatic rollback. Recovery is the manual restore above.
- The `mariadb` container is never recreated or upgraded by a deploy. Its passwords and the published `3306` stay
  as they are in `docker-compose.yml`.
- The first deploy into a new `DIR` over a stack started from another folder does not compare that folder's
  compose file. The uploaded one becomes the server's copy.
- The device precheck reads `- /dev/x:...` list entries, quoted or not. The long `devices:` syntax is not checked.
- Folder names allow only letters, digits and `. _ -`. Jar versions allow the same, so `3.1-SNAPSHOT` is fine.
- Password login only. The host key is trusted on first use: check the fingerprint ssh shows on the first connection.
- Windows: checked on Windows 11 with Windows PowerShell 5.1 against an Ubuntu server, from a checkout path
  with a space: the password prompt works while the archive goes through stdin, and deploy, update,
  `-Status` and `-Rollback` ran end to end. The archive (jar hash compared) was also checked with an ssh stub
  under PS 5.1 at code pages 65001 and 852 and under PowerShell 7. `-Attach` was not run on Windows.
- Linux client: `deploy.sh` has run only with a stubbed ssh. Every run against the real server used `deploy.ps1`.
- Not yet exercised on a real server: the automatic rollback after a failed start, the pre-migration dump and the
  restore procedure, the compose-diff abort, image pruning beyond 3 tags, and `--attach` / `-Attach` (on any client).
  The database drop before a restore and the return to the running image after a failed `--rollback` were
  checked only against stub `docker` and `mariadb` commands. The live rollback test used two tags of the same image, so it proved the mechanics only.
- The deploy user is in the `docker` group, which is root-equivalent on the server.

## Troubleshooting

- `another deploy is running`: a live deploy holds the lock. A lock left by a killed deploy is cleared automatically.
- `device /dev/... does not exist`: the compose file maps a serial device this host does not have.
  Edit `docker-compose.yml` in the repo and deploy with `--update-compose`.
- Logs of every run are in `DIR/deploy-logs/`, and `--status` shows what runs now.
