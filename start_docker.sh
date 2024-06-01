#!/bin/bash

# Check if docker-compose is installed
if ! command -v docker-compose &> /dev/null
then
    echo "docker-compose could not be found, please install it."
    exit 1
fi

# Run docker-compose build
echo "Building Docker images..."
docker compose build

if [ $? -ne 0 ]; then
    echo "Docker-compose build failed."
    exit 1
fi

# Run docker-compose up
echo "Starting Docker containers..."
docker compose up

if [ $? -ne 0 ]; then
    echo "Docker-compose up failed."
    exit 1
fi