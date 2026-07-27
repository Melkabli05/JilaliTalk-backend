#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Kill any existing Java processes on port 8080
lsof -ti:8080 | xargs kill -9 2>/dev/null
sleep 1

# Local-dev SQLite path: the repo's ./data/ already holds the dev DB.
# The default /home/app/data/jilalitalk.db is for the production container (WORKDIR /home/app);
# /home/app doesn't exist on dev hosts, and Micronaut 5 eager-initializes Hikari before
# DbDirectoryInitializer's @PostConstruct runs. Override JILALI_DB_PATH for local runs.
export JILALI_DB_PATH="${JILALI_DB_PATH:-$SCRIPT_DIR/data/jilalitalk.db}"

# Start the Micronaut BFF
exec ./gradlew clean classes run