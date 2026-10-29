#!/usr/bin/env bash
# macOS / Linux launcher. Uses the built JAR, building it first with Maven (Java 21) if needed.
set -e
cd "$(dirname "$0")"
if [ ! -f target/metropolitan.jar ]; then
  command -v mvn >/dev/null || { echo "Maven not found. Install Java 21 + Maven, or use: docker compose up --build"; exit 1; }
  mvn -q -DskipTests package
fi
echo "Starting Metropolitan on http://localhost:${PORT:-8080}  (local H2 database in ./data)"
exec java -jar target/metropolitan.jar
