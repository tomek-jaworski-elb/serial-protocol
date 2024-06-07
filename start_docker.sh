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

docker compose down

if [ $? -ne 0 ]; then
    echo "Docker compose down failed."
    exit 1
fi

# Run docker compose build
echo "Building Docker images..."
docker compose build

if [ $? -ne 0 ]; then
    echo "Docker compose build failed."
    exit 1
fi

# Run docker compose up
echo "Starting Docker containers..."
docker compose up

if [ $? -ne 0 ]; then
    echo "Docker compose up failed."
    exit 1
fi