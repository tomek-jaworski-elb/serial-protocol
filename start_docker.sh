#!/bin/bash
# Rebuild the app image and (re)start the stack from this project folder.
# Usage: ./start_docker.sh [-d]   (-d = detached, waits until the app is healthy)
# Everything that can fail runs before the running stack is touched; the database container
# is only started if needed, never recreated. Not for a server folder managed by deploy/deploy.sh.

cd "$(dirname "$0")" || exit 1
container_name="serial-ports-server"

die() { echo "ERROR: $*" >&2; exit 1; }

docker compose version >/dev/null 2>&1 || die "docker compose could not be found, please install it."

if [ -f docker-compose.override.yml ] && [ -f .project ]; then
    die "this folder is managed by deploy/deploy.sh (no Dockerfile or jar here). Use 'docker compose up -d' here, or deploy.sh --status / --rollback."
fi
version=$(sed -n 's/^ARG VERSION=//p' Dockerfile 2>/dev/null)
[ -n "$version" ] || die "no Dockerfile with 'ARG VERSION=' in $(pwd)"
[ -f "target/serial-protocol-$version.jar" ] || die "target/serial-protocol-$version.jar is missing; build it first: ./mvnw clean package -Dmaven.test.skip=true"
[ -f cert/server.p12 ] || die "cert/server.p12 is missing"
# Keep this sed in sync with deploy/deploy-remote.sh.
for dev in $(sed -n "s/^[[:space:]]*-[[:space:]]*[\"']\{0,1\}\(\/dev[^:\"'[:space:]]*\):.*/\1/p" docker-compose.yml); do
    [ -e "$dev" ] || die "device $dev from docker-compose.yml does not exist on this host"
done
echo "Using target/serial-protocol-$version.jar built $(date -r "target/serial-protocol-$version.jar" '+%Y-%m-%d %H:%M')"

echo "Building the image (the running stack keeps serving)..."
docker compose build server || die "image build failed; nothing was stopped."

# Remove the app container by name too, in case it was started outside this compose project.
if [ -n "$(docker ps -aq -f name="^${container_name}\$")" ]; then
    echo "Replacing the running \"$container_name\"..."
    docker rm -f "$container_name" >/dev/null || die "cannot remove $container_name"
fi

if [ "$1" != "-d" ]; then
    echo "Starting Docker containers (Ctrl+C stops them)..."
    exec docker compose up --no-build
fi

echo "Starting Docker containers in detached mode..."
docker compose up -d --no-build || die "docker compose up failed."
for i in $(seq 1 90); do
    status=$(docker inspect -f '{{.State.Health.Status}}' "$container_name" 2>/dev/null)
    [ "$status" = healthy ] && { echo "$container_name is healthy."; exit 0; }
    sleep 2
done
docker logs --tail 40 "$container_name"
die "$container_name is not healthy after 180 s (status: ${status:-unknown})."
