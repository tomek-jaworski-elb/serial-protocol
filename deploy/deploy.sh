#!/usr/bin/env bash
# Deploy serial-protocol to a Linux Docker server over one ssh connection (Linux client).
# The ssh password is typed into ssh's own prompt: this script never reads or stores it.
set -euo pipefail

cd "$(dirname "$0")/.."
DEPLOY_HOST= DEPLOY_PORT= DEPLOY_USER= DEPLOY_DIR=
action=deploy skip_build= yes= remote_flags=()

die() { echo "ERROR: $*" >&2; exit 1; }

usage() {
    cat <<'TXT'
Usage: deploy/deploy.sh [options]

Builds the jar, uploads it with the Dockerfile, docker-compose.yml and cert/server.p12 over ONE
ssh connection, builds the image on the server while the running version keeps serving, then
swaps the app container (about 15-35 s of downtime) and rolls back if the new one is not healthy.
ssh asks for the password itself; it is never read, passed or stored by this script.
Values not given as options are asked for, with defaults from deploy/deploy.conf
(or deploy/deploy.conf.example).

Options:
  --host H           server IP or host name
  --port P           ssh port (default 22)
  --user U           ssh user (needs Docker access on the server)
  --dir D            absolute folder on the server, e.g. /opt/serial-protocol
  --skip-build       do not run Maven; use the existing target/serial-protocol-*.jar
  --update-compose   replace the server's docker-compose.yml when it differs (shows a diff first)
  --backup           dump the database before the swap even without pending migrations
  --yes              do not ask for the final confirmation
  --rollback         switch back to the previously deployed image
  --status           show the current and previous image and the containers
  --attach           follow the log of the last deploy again (e.g. after a dropped connection)
  -h, --help         show this help

See deploy/README.md.
TXT
}

# KEY=VALUE allowlist; the file is never sourced.
read_conf() {
    local k v
    while IFS='=' read -r k v || [[ -n $k ]]; do
        k=${k//[[:space:]]/}; v=${v%%#*}; v=${v//[[:space:]]/}
        case $k in
            ''|'#'*) ;;
            DEPLOY_HOST|DEPLOY_PORT|DEPLOY_USER|DEPLOY_DIR) [[ -n ${!k} ]] || printf -v "$k" '%s' "$v" ;;
            *) echo "WARNING: $1: ignoring unknown key '$k'" >&2 ;;
        esac
    done < "$1"
}

while (($#)); do
    case $1 in
        --host) DEPLOY_HOST=${2:?}; shift ;;
        --port) DEPLOY_PORT=${2:?}; shift ;;
        --user) DEPLOY_USER=${2:?}; shift ;;
        --dir) DEPLOY_DIR=${2:?}; shift ;;
        --skip-build) skip_build=1 ;;
        --update-compose|--backup) remote_flags+=("$1") ;;
        --yes) yes=1 ;;
        --rollback) action=rollback ;;
        --status) action=status ;;
        --attach) action=attach ;;
        --pass*|--pw*) die "passwords are never accepted as options; ssh asks for it" ;;
        -h|--help) usage; exit 0 ;;
        *) die "unknown option $1" ;;
    esac
    shift
done

cli="${DEPLOY_HOST:+H}${DEPLOY_PORT:+P}${DEPLOY_USER:+U}${DEPLOY_DIR:+D}"
for f in deploy/deploy.conf deploy/deploy.conf.example; do
    [[ -f $f ]] && read_conf "$f"
done

ask() {  # ask VAR LETTER "Label"; not asked when given as an option
    local ans
    [[ $cli == *$2* ]] && return 0
    read -r -p "$3 [${!1}]: " ans || die "no input"
    [[ -n $ans ]] && printf -v "$1" '%s' "$ans"
    return 0
}
ask DEPLOY_HOST H "Server IP or host"
ask DEPLOY_PORT P "SSH port"
ask DEPLOY_USER U "SSH user"
ask DEPLOY_DIR D "Folder on the server"
DEPLOY_PORT=${DEPLOY_PORT:-22}

# Everything that reaches the remote command line is validated here, so it needs no quoting.
[[ $DEPLOY_HOST =~ ^[A-Za-z0-9.:-]+$ && $DEPLOY_HOST != -* ]] || die "invalid host '$DEPLOY_HOST'"
[[ $DEPLOY_PORT =~ ^[0-9]{1,5}$ ]] || die "invalid port '$DEPLOY_PORT'"
[[ $DEPLOY_USER =~ ^[A-Za-z_][A-Za-z0-9_.-]*$ ]] || die "invalid user '$DEPLOY_USER'"
DEPLOY_DIR=${DEPLOY_DIR%/}
[[ $DEPLOY_DIR =~ ^/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)+$ && $DEPLOY_DIR != *..* ]] \
    || die "invalid folder '$DEPLOY_DIR' (absolute, at least two levels, letters, digits . _ - only)"

ssh_cmd=(ssh -p "$DEPLOY_PORT"
    -o ServerAliveInterval=15 -o ServerAliveCountMax=4
    -o StrictHostKeyChecking=accept-new -o "UserKnownHostsFile=\"${PWD//%/%%}/deploy/.known_hosts\""
    -o PubkeyAuthentication=no -o PreferredAuthentications=password,keyboard-interactive
    "$DEPLOY_USER@$DEPLOY_HOST")

if [[ $action != deploy ]]; then
    exec "${ssh_cmd[@]}" "sh $DEPLOY_DIR/deploy-remote.sh $action $DEPLOY_DIR"
fi

[[ -f cert/server.p12 ]] || echo "WARNING: no local cert/server.p12; the server's copy will be used" >&2
if [[ -z $skip_build ]]; then
    bash mvnw -q clean package -Dmaven.test.skip=true
fi
shopt -s nullglob
jars=(target/serial-protocol-*.jar)
((${#jars[@]} == 1)) || die "expected exactly one target/serial-protocol-*.jar, found ${#jars[@]}"
version=${jars[0]#target/serial-protocol-}; version=${version%.jar}
# The version goes into the remote command line unquoted, so it must be plain.
[[ $version =~ ^[0-9A-Za-z._-]+$ ]] || die "unsupported characters in jar version '$version'"
tag=$version-$(date +%Y%m%d%H%M%S)

if [[ -z $yes ]]; then
    read -r -p "Deploy $tag to $DEPLOY_USER@$DEPLOY_HOST:$DEPLOY_DIR? [y/N] " ans
    [[ $ans == [yY]* ]] || die "cancelled"
fi

stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
mkdir -p "$stage/build/target" "$stage/build/cert"
tr -d '\r' < Dockerfile > "$stage/build/Dockerfile"
cp "${jars[0]}" "$stage/build/target/"
[[ -f cert/server.p12 ]] && cp cert/server.p12 "$stage/build/cert/"
tr -d '\r' < docker-compose.yml > "$stage/docker-compose.yml"
tr -d '\r' < deploy/deploy-remote.sh > "$stage/deploy-remote.sh"
# Migrations of the jar being deployed, not of the working tree (it may be a different commit).
if command -v jar >/dev/null; then jar tf "${jars[0]}"; else unzip -Z1 "${jars[0]}"; fi \
    | sed -n 's#^BOOT-INF/classes/db/migration/\(..*\)#\1#p' > "$stage/MIGRATIONS"
[[ -s $stage/MIGRATIONS ]] || die "no Flyway migrations found in ${jars[0]}"

inc=$DEPLOY_DIR/.incoming-$tag
echo "Uploading and deploying; ssh will ask for the password once."
tar czf - -C "$stage" . | "${ssh_cmd[@]}" \
    "umask 077 && mkdir -p $inc && tar xzf - -C $inc && exec sh $inc/deploy-remote.sh start $DEPLOY_DIR $inc $tag ${remote_flags[*]-}"
