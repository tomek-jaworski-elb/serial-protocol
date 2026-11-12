#!/bin/sh
# Server side of the deploy (POSIX sh). Shipped in every upload by deploy.sh / deploy.ps1.
#   start  DIR INC TAG [--update-compose] [--backup]   unpacked upload in INC -> deploy, detached
#   rollback DIR | attach DIR | status DIR
# Docs: deploy/README.md. Never touches volumes; never recreates mariadb.

APP=serial-ports-server
DB=mariadb
IMAGE=serial-protocol
KEEP_TAGS=3
KEEP_DUMPS=5
HEALTH_TIMEOUT=180

usage() {
    cat <<'TXT'
Usage: deploy-remote.sh COMMAND DIR [ARGS]   (normally run by deploy.sh / deploy.ps1 over ssh)

  start DIR INC TAG [--update-compose] [--backup]
                 deploy the upload unpacked in INC (DIR/.incoming-TAG), detached from the session
  rollback DIR   switch back to the image in DIR/previous-tag
  attach DIR     follow the log of the last run again
  status DIR     show the current and previous image and the containers
  -h, --help     show this help

Logs: DIR/deploy-logs. DB dumps: DIR/backups. See deploy/README.md.
TXT
}

say() { printf '%s %s\n' "$(date '+%H:%M:%S')" "$*"; }
die() { say "ERROR: $*"; exit 1; }

# Same rule as the clients: absolute, safe characters, no '..', at least two components.
valid_dir() {
    case $1 in
        *..* | *//* ) return 1 ;;
    esac
    printf '%s' "$1" | grep -Eq '^/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)+/?$'
}

umask 077  # logs, dumps, state files and the cert are for the deploy user only
cmd=${1:-}
case $cmd in -h|--help|help|'') usage; exit 0 ;; esac
DIR=${2:-}
valid_dir "$DIR" || die "invalid deploy folder '$DIR'"
DIR=${DIR%/}
LOGS=$DIR/deploy-logs

# ---------- helpers ----------

# The upload folder is exactly DIR/.incoming-TAG; it is rm -rf'd when the run ends.
valid_upload() {
    printf '%s' "$2" | grep -Eq '^[0-9A-Za-z._-]+-[0-9]{14}$' || die "invalid tag '$2'"
    [ "$1" = "$DIR/.incoming-$2" ] || die "upload folder must be $DIR/.incoming-$2"
}

dc() { docker compose -p "$PROJECT" --project-directory "$DIR" -f "$DIR/docker-compose.yml" -f "$DIR/docker-compose.override.yml" "$@"; }

exists() { docker inspect "$1" >/dev/null 2>&1; }

label() { docker "$1" inspect -f '{{index .Labels "com.docker.compose.project"}}' "$2" 2>/dev/null; }

# The root password and database name are read inside the container, never on the host's argv.
sql() { docker exec "$DB" sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb -uroot -N -B "$MARIADB_DATABASE" -e "$1"' sh "$1"; }

applied_versions() { sql 'select version from flyway_schema_history where success = 1' 2>/dev/null; }

# All rows, failed ones included: a half-applied migration (MariaDB DDL is not transactional) counts.
history_rows() { sql 'select count(*) from flyway_schema_history' 2>/dev/null || echo 0; }

write_override() {
    printf 'services:\n  %s:\n    image: %s\n' server "$1" > "$DIR/docker-compose.override.yml"
}

wait_db() {
    i=0
    while [ "$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$DB" 2>/dev/null)" != healthy ]; do
        i=$((i + 2)); [ $i -gt 120 ] && return 1; sleep 2
    done
}

# 0 when healthy; 1 as soon as the container stops, restarts or times out.
wait_app() {
    i=0
    while :; do
        s=$(docker inspect -f '{{.State.Status}} {{.State.Restarting}} {{.RestartCount}} {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$APP" 2>/dev/null) || return 1
        set -- $s
        [ "$1" = running ] && [ "$2" = false ] && [ "$3" = 0 ] || { say "container is $s"; return 1; }
        [ "$4" = healthy ] || [ "$4" = none ] && return 0
        i=$((i + 2)); [ $i -gt $HEALTH_TIMEOUT ] && { say "not healthy after ${HEALTH_TIMEOUT}s"; return 1; }
        sleep 2
    done
}

# Replace the app container with image $1; only this is downtime.
swap() {
    write_override "$1"
    if exists "$APP"; then
        docker stop -t 30 "$APP" >/dev/null || return 1
        docker rm "$APP" >/dev/null || return 1
    fi
    dc up -d --no-deps --no-build server
}

# A pid only counts when it is still a deploy-remote.sh (pids are reused after a reboot).
alive() {
    kill -0 "$1" 2>/dev/null || return 1
    [ -r "/proc/$1/cmdline" ] || return 0
    tr '\000' ' ' < "/proc/$1/cmdline" | grep -q deploy-remote
}

lock() {
    mkdir -p "$LOGS"
    tries=0
    while ! mkdir "$DIR/.lock" 2>/dev/null; do
        tries=$((tries + 1)); [ $tries -le 5 ] || die "cannot take the lock $DIR/.lock"
        [ -d "$DIR/.lock" ] || continue  # released meanwhile
        pid=$(cat "$DIR/.lock/pid" 2>/dev/null)
        if [ -n "$pid" ]; then
            alive "$pid" && die "another deploy is running (pid $pid, see $LOGS)"
        elif [ -z "$(find "$DIR/.lock" -maxdepth 0 -mmin +1 2>/dev/null)" ]; then
            die "another deploy is just starting; try again"
        fi
        say "removing a stale lock (pid ${pid:-none})"
        # Rename first: of two runs reaping the same stale lock only one wins the mv.
        mv "$DIR/.lock" "$DIR/.lock.stale.$$" 2>/dev/null || continue
        if [ "$(cat "$DIR/.lock.stale.$$/pid" 2>/dev/null)" != "$pid" ]; then
            # Another run reaped it first and we moved its fresh lock: give it back.
            mv "$DIR/.lock.stale.$$" "$DIR/.lock" 2>/dev/null
            die "another deploy took the lock meanwhile; try again"
        fi
        rm -rf "$DIR/.lock.stale.$$"
    done
    echo $$ > "$DIR/.lock/pid"
    LOCKED=1
    echo "$RUN_ID" > "$LOGS/last"  # only a run that holds the lock is what --attach follows
}

prechecks() {
    docker info >/dev/null 2>&1 || die "docker is not usable by $(id -un) (docker group?)"
    docker compose version >/dev/null 2>&1 || die "docker compose v2 plugin is missing"
    for d in "$DIR" "$(docker info -f '{{.DockerRootDir}}' 2>/dev/null || echo /var/lib)"; do
        free=$(df -Pk "$d" | awk 'NR==2 {print $4}')
        [ "${free:-0}" -ge 2097152 ] || die "less than 2 GB free on $d"
    done
    for dev in $(sed -n "s/^[[:space:]]*-[[:space:]]*[\"']\{0,1\}\(\/dev[^:\"'[:space:]]*\):.*/\1/p" "$1"); do
        [ -e "$dev" ] || die "device $dev from docker-compose.yml does not exist on this host"
    done
}

# Adopt the compose project of the running stack; never guess from the folder name.
project() {
    PROJECT=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$APP" 2>/dev/null) \
        || PROJECT=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$DB" 2>/dev/null) \
        || PROJECT=$(cat "$DIR/.project" 2>/dev/null) \
        || PROJECT=serial-protocol
    [ -n "$PROJECT" ] || PROJECT=serial-protocol
    for v in db-data server-logs; do
        l=$(label volume "$v") && [ -n "$l" ] && [ "$l" != "$PROJECT" ] && die "volume $v belongs to compose project '$l', not '$PROJECT'"
    done
    l=$(label network backend) && [ -n "$l" ] && [ "$l" != "$PROJECT" ] && die "network backend belongs to compose project '$l', not '$PROJECT'"
    echo "$PROJECT" > "$DIR/.project"
    say "compose project: $PROJECT"
}

prune_images() {
    keep_prev=$(cat "$DIR/previous-tag" 2>/dev/null)
    docker image ls "$IMAGE" --format '{{.Tag}}' | grep -v '^prev-' | sed 's/.*-\([0-9]*\)$/\1 &/' | sort -r \
        | awk -v n=$KEEP_TAGS 'NR > n {print $2}' | while read -r t; do
            [ "$IMAGE:$t" = "$keep_prev" ] || docker rmi "$IMAGE:$t" >/dev/null 2>&1 || true
        done
    docker image ls "$IMAGE" --format '{{.Tag}}' | grep '^prev-' | while read -r t; do
        [ "$IMAGE:$t" = "$keep_prev" ] || docker rmi "$IMAGE:$t" >/dev/null 2>&1 || true
    done
}

backup() {
    mkdir -p "$DIR/backups"
    out=$DIR/backups/db-$TAG.sql
    say "dumping the database to $out.gz (the old version keeps serving)"
    (umask 077
     docker exec "$DB" sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb-dump -uroot --single-transaction --routines --triggers "$MARIADB_DATABASE"' > "$out.tmp") \
        || { rm -f "$out.tmp"; die "database dump failed"; }
    tail -c 200 "$out.tmp" | grep -q 'Dump completed' || { rm -f "$out.tmp"; die "database dump is incomplete"; }
    mv "$out.tmp" "$out" && gzip -f "$out" || die "cannot compress the dump"
    ls -1t "$DIR"/backups/db-*.sql.gz | tail -n +$((KEEP_DUMPS + 1)) | while read -r f; do rm -f "$f"; done
    BACKUP=$out.gz
}

restore_hint() {
    say "The schema was migrated, so the previous version cannot start on it. To go back:"
    say "  1. docker stop $APP"
    say "  2. gunzip -c ${BACKUP:-<dump>} | docker exec -i $DB sh -c 'MYSQL_PWD=\"\$MARIADB_ROOT_PASSWORD\" exec mariadb -uroot \"\$MARIADB_DATABASE\"'"
    say "  3. sh $DIR/deploy-remote.sh rollback $DIR"
}

# ---------- actions ----------

do_deploy() {
    valid_upload "$1" "$2"
    INC=$1 TAG=$2; shift 2
    UPDATE_COMPOSE= FORCE_BACKUP=
    for a in "$@"; do
        case $a in
            --update-compose) UPDATE_COMPOSE=1 ;;
            --backup) FORCE_BACKUP=1 ;;
            *) die "unknown option $a" ;;
        esac
    done
    lock
    find "$DIR" -maxdepth 1 -name '.incoming-*' ! -path "$INC" -mmin +60 -exec rm -rf {} + 2>/dev/null
    [ -f "$INC/build/Dockerfile" ] && [ -f "$INC/docker-compose.yml" ] || die "upload is incomplete"
    prechecks "$INC/docker-compose.yml"
    project

    if [ ! -f "$INC/build/cert/server.p12" ]; then
        [ -f "$DIR/cert/server.p12" ] || die "no cert/server.p12 in the upload nor in $DIR/cert"
        mkdir -p "$INC/build/cert" && cp "$DIR/cert/server.p12" "$INC/build/cert/"
    fi
    jar=$(ls "$INC"/build/target/serial-protocol-*.jar 2>/dev/null)
    [ "$(printf '%s\n' "$jar" | grep -c .)" = 1 ] || die "expected exactly one jar in the upload"
    version=${jar##*/serial-protocol-}; version=${version%.jar}

    if [ -f "$DIR/docker-compose.yml" ] && ! cmp -s "$INC/docker-compose.yml" "$DIR/docker-compose.yml"; then
        say "docker-compose.yml on the server differs from the uploaded one:"
        diff -u "$DIR/docker-compose.yml" "$INC/docker-compose.yml" || true
        [ -n "$UPDATE_COMPOSE" ] || die "nothing changed; run again with --update-compose to replace it"
    fi

    say "building image $IMAGE:$TAG (the running version keeps serving)"
    docker build --build-arg "VERSION=$version" -t "$IMAGE:$TAG" "$INC/build" || die "image build failed, nothing was stopped"

    # Files go in only after a good build.
    mkdir -p "$DIR/cert"
    rm -f "$DIR/docker-compose.yml.prev"
    [ -f "$DIR/docker-compose.yml" ] && cp "$DIR/docker-compose.yml" "$DIR/docker-compose.yml.prev"
    cp "$INC/docker-compose.yml" "$DIR/docker-compose.yml"
    (umask 077; cp "$INC/build/cert/server.p12" "$DIR/cert/server.p12.new") && mv "$DIR/cert/server.p12.new" "$DIR/cert/server.p12"
    cp "$INC/deploy-remote.sh" "$DIR/deploy-remote.sh"
    [ -f "$DIR/docker-compose.override.yml" ] || write_override "$IMAGE:$TAG"

    say "starting the database if needed (never recreated)"
    dc up -d --no-recreate "$DB" || die "cannot start $DB"
    wait_db || die "$DB is not healthy"

    PREV=
    if exists "$APP"; then
        PREV=$(docker inspect -f '{{.Config.Image}}' "$APP")
        case $PREV in
            "$IMAGE":*) ;;
            *) docker tag "$(docker inspect -f '{{.Image}}' "$APP")" "$IMAGE:prev-$TAG" && PREV=$IMAGE:prev-$TAG ;;
        esac
        say "previous image: $PREV"
    fi
    m0=$(history_rows)
    pending=
    # No readable history (new or pre-Flyway database, or a query error): dump to be safe.
    applied=$(applied_versions) || pending=" (history not readable)"
    for f in $(grep -o '^V[0-9][0-9._]*__' "$INC/MIGRATIONS" 2>/dev/null); do
        v=${f#V}; v=${v%__}; v=$(printf '%s' "$v" | tr _ .)
        printf '%s\n' "$applied" | grep -qx "$v" || pending="$pending V$v"
    done
    [ -n "$pending" ] && say "pending migrations:$pending"
    BACKUP=
    if [ -n "$pending" ] || [ -n "$FORCE_BACKUP" ]; then backup; fi

    trap '' INT TERM  # from here on a signal must not leave the server without the app
    say "swapping $APP to $IMAGE:$TAG (downtime starts)"
    t0=$(date +%s)
    if swap "$IMAGE:$TAG" && wait_app; then
        say "healthy after $(( $(date +%s) - t0 ))s of downtime"
        echo "$IMAGE:$TAG" > "$DIR/current-tag"
        [ -n "$PREV" ] && echo "$PREV" > "$DIR/previous-tag"
        prune_images
        dc ps
        say "deployed $IMAGE:$TAG"
        return 0
    fi

    say "the new version failed to start; last log lines:"
    docker logs --tail 80 "$APP" 2>&1
    m1=$(history_rows)
    if [ -z "$PREV" ]; then
        die "first install failed; no previous version to go back to"
    elif [ "$m1" != "$m0" ]; then
        # Record the failed state, so "rollback" after the restore goes to PREV, not to an older one.
        echo "$IMAGE:$TAG" > "$DIR/current-tag"
        echo "$PREV" > "$DIR/previous-tag"
        restore_hint
        die "deploy failed after a schema migration; no automatic rollback"
    fi
    say "rolling back to $PREV"
    # The old image goes back with the compose file it ran with.
    [ -f "$DIR/docker-compose.yml.prev" ] && mv "$DIR/docker-compose.yml.prev" "$DIR/docker-compose.yml"
    if swap "$PREV" && wait_app; then
        docker rmi "$IMAGE:$TAG" >/dev/null 2>&1
        die "deploy failed, rolled back to $PREV"
    fi
    die "deploy failed and the rollback to $PREV failed too; check 'docker logs $APP'"
}

do_rollback() {
    lock
    prechecks "$DIR/docker-compose.yml"
    project
    prev=$(cat "$DIR/previous-tag" 2>/dev/null) || die "no previous version recorded"
    cur=$(cat "$DIR/current-tag" 2>/dev/null)
    docker image inspect "$prev" >/dev/null 2>&1 || die "image $prev no longer exists; nothing was stopped"
    trap '' INT TERM
    say "rolling back from ${cur:-?} to $prev"
    if swap "$prev" && wait_app; then
        echo "$prev" > "$DIR/current-tag"
        [ -n "$cur" ] && echo "$cur" > "$DIR/previous-tag"
        say "rolled back to $prev"
        return 0
    fi
    docker logs --tail 80 "$APP" 2>&1
    die "rollback failed (after a migration, restore the dump first)"
}

# Start "$@" detached from the ssh session (it survives a dropped connection), then follow it.
launch() {
    script=$1 id=$2; shift 2
    mkdir -p "$LOGS"
    if command -v setsid >/dev/null 2>&1; then s=setsid; else s=; fi
    $s nohup sh "$script" _run "$DIR" "$id" "$@" > "$LOGS/$id.log" 2>&1 < /dev/null &
    follow "$id"
}

follow() {
    log=$LOGS/$1.log rc=$LOGS/$1.rc
    tail -n +1 -f "$log" & t=$!
    i=0
    while [ ! -f "$rc" ]; do
        sleep 1; i=$((i + 1))
        # The run writes its pid first; stop waiting if it vanished without an exit code.
        p=$(cat "$LOGS/$1.pid" 2>/dev/null)
        [ -n "$p" ] && ! kill -0 "$p" 2>/dev/null && [ ! -f "$rc" ] && { echo 99 > "$rc"; }
        [ -z "$p" ] && [ $i -gt 30 ] && echo 98 > "$rc"
    done
    sleep 1; kill $t 2>/dev/null
    exit "$(cat "$rc")"
}

# Internal entry of the detached run: ID deploy|rollback ARGS
run() {
    id=$1 what=$2; shift 2
    RUN_ID=$id
    trap '' HUP
    echo $$ > "$LOGS/$id.pid"
    trap 'rc=$?; [ -n "${LOCKED:-}" ] && rm -rf "$DIR/.lock"; [ -n "${INC:-}" ] && rm -rf "$INC"; echo $rc > "$LOGS/$id.rc"' EXIT
    trap 'exit 130' INT TERM
    case $what in
        deploy) do_deploy "$@" ;;
        rollback) do_rollback ;;
    esac
}

case $cmd in
    start)
        valid_upload "${3:-}" "${4:-}"
        inc=$3 tag=$4; shift 4
        # Only the upload may be in a folder that no deploy created; refuse to write anything else there.
        if [ ! -f "$DIR/.project" ] && ls -A "$DIR" | grep -Evq '^(deploy-logs|\.lock|\.incoming-.*)$'; then
            rm -rf "$inc"
            die "$DIR is not empty and was not created by a deploy; choose an empty or new folder"
        fi
        launch "$inc/deploy-remote.sh" "deploy-$tag" deploy "$inc" "$tag" "$@" ;;
    rollback)
        [ -f "$DIR/deploy-remote.sh" ] || die "nothing deployed in $DIR"
        launch "$DIR/deploy-remote.sh" "rollback-$(date +%Y%m%d%H%M%S)" rollback ;;
    attach)
        id=$(cat "$LOGS/last" 2>/dev/null) || die "no deploy log in $LOGS"
        follow "$id" ;;
    status)
        [ -f "$DIR/current-tag" ] && PROJECT=$(cat "$DIR/.project" 2>/dev/null) || die "nothing deployed in $DIR"
        echo "current:  $(cat "$DIR/current-tag" 2>/dev/null)"
        echo "previous: $(cat "$DIR/previous-tag" 2>/dev/null)"
        dc ps ;;
    _run)
        shift 2; run "$@" ;;
    *)
        die "usage: deploy-remote.sh start|rollback|attach|status DIR ..." ;;
esac
