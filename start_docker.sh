#!/bin/bash

# Check if docker compose is installed
docker compose version

if [ $? -ne 0 ];
then
    echo "docker compose could not be found, please install it."
    exit 1
fi

# Check if a container with the name "serial-ports-server" is running or exists
container_name="serial-ports-server"
existing_container=$(docker ps -aq -f name="$container_name")

if [ -n "$existing_container" ]; then
  echo "A container with the name \"$container_name\" already exists. Removing it..."
  docker stop "$container_name"
  docker rm "$container_name"
fi

docker compose stop

if [ $? -ne 0 ]; then
    echo "Docker compose down failed."
    exit 1
fi

# Determine whether to run in detached mode or not based on the argument
if [ "$1" == "-d" ]; then
    echo "Starting Docker containers in detached mode..."
    docker compose up --build -d --force-recreate --always-recreate-deps
else
    echo "Starting Docker containers..."
    docker compose up --build --force-recreate --always-recreate-deps
fi

if [ $? -ne 0 ]; then
    echo "Docker compose up failed."
    exit 1
fi