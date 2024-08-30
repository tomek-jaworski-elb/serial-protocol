#!/bin/bash

# down service
docker compose down

if [ $? -ne 0 ]; then
  echo "Docker compose down failed."
  exit 1
fi