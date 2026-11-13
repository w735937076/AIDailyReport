#!/usr/bin/env bash
# Build a Windows-friendly distribution (run on Windows with Git Bash, or adapt paths).
# Prerequisites: JDK 17+, Maven, jpackage (included in JDK 17+)

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> Maven package"
mvn -q -DskipTests package

JAR="$ROOT/target/dailyreport-1.0.0.jar"
OUT="$ROOT/dist/win"
NAME="AIDailyReport"

rm -rf "$OUT"
mkdir -p "$ROOT/dist"

if command -v jpackage >/dev/null 2>&1; then
  echo "==> jpackage (requires Windows host for .exe; on Linux builds app-image only)"
  jpackage \
    --input "$ROOT/target" \
    --name "$NAME" \
    --main-jar dailyreport-1.0.0.jar \
    --main-class org.springframework.boot.loader.launch.JarLauncher \
    --type app-image \
    --dest "$OUT" \
    --app-version 1.0.0 \
    --description "Git commits to AI-enhanced daily reports" \
    --vendor "AIDailyReport" \
    --java-options "-Dfile.encoding=UTF-8" \
    --java-options "-Dapp.open-browser=true" || true
  echo "Output: $OUT"
else
  echo "jpackage not found; fat jar only: $JAR"
fi

echo "Done. Double-click or run: java -jar $JAR"
