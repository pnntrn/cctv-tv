#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_classes_dir=$(mktemp -d)
trap 'rm -rf "$test_classes_dir"' EXIT
if command -v javac >/dev/null 2>&1; then
  compiler=(javac)
else
  compiler=(java com.sun.tools.javac.Main)
fi
"${compiler[@]}" -encoding UTF-8 -d "$test_classes_dir" \
  app/src/main/java/com/pnntrn/cctv/CameraRules.java \
  app/src/main/java/com/pnntrn/cctv/Camera.java \
  app/src/main/java/com/pnntrn/cctv/Location.java \
  app/src/main/java/com/pnntrn/cctv/Nvr.java \
  app/src/main/java/com/pnntrn/cctv/PlaybackFailure.java \
  app/src/main/java/com/pnntrn/cctv/RtspProbe.java \
  app/src/main/java/com/pnntrn/cctv/PairingSession.java \
  app/src/test/java/com/pnntrn/cctv/CoreChecks.java \
  app/src/test/java/com/pnntrn/cctv/ExtendedChecks.java
java -cp "$test_classes_dir" com.pnntrn.cctv.CoreChecks

java -cp "$test_classes_dir" com.pnntrn.cctv.ExtendedChecks
