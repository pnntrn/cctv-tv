#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes_dir=$(mktemp -d)
trap 'rm -rf "$classes_dir"' EXIT
if command -v javac >/dev/null 2>&1; then compiler=(javac); else compiler=(java com.sun.tools.javac.Main); fi
"${compiler[@]}" -encoding UTF-8 -d "$classes_dir" \
  app/src/main/java/com/pnntrn/cctv/CameraRules.java \
  app/src/main/java/com/pnntrn/cctv/PlaybackFailure.java \
  app/src/main/java/com/pnntrn/cctv/RtspProbe.java scripts/DigestCheck.java
java -cp "$classes_dir" com.pnntrn.cctv.DigestCheck
