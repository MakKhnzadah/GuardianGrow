#!/usr/bin/env sh
# Repo-root shim to run the real Gradle build located in backend/
set -eu
ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
cd "$ROOT_DIR/backend"
exec "./gradlew" "$@"
