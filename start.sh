#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Kill any existing Java processes on port 8080
lsof -ti:8080 | xargs kill -9 2>/dev/null
sleep 1

# Start the Micronaut BFF
exec ./gradlew clean classes run