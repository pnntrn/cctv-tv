#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
update_classes=$(mktemp -d)
trap 'rm -rf "$update_classes"' EXIT
java com.sun.tools.javac.Main -d "$update_classes" app/src/main/java/com/pnntrn/cctv/UpdatePolicy.java app/src/main/java/com/pnntrn/cctv/UpdateTransfer.java app/src/test/java/com/pnntrn/cctv/UpdateChecks.java
java -cp "$update_classes" com.pnntrn.cctv.UpdateChecks
