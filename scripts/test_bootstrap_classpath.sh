#!/bin/sh
# Regression guard for Android 17 / ART bootstrap environment.
# Run from the repository root: sh scripts/test_bootstrap_classpath.sh
set -eu
repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
service="$repo_root/app/src/main/java/com/shizuposed/manager/service/ShizuPosedService.java"

if [ ! -f "$service" ]; then
  echo "FAIL: ShizuPosedService.java is missing" >&2
  exit 2
fi

# app_process64 needs the inherited Android ART boot classpaths.
# We intentionally clear CLASSPATH, which points at the app bootstrap DEX,
# but never the runtime boot classpath variables.
if grep -nE 'unset[[:space:]]+(BOOTCLASSPATH|DEX2OATBOOTCLASSPATH)' "$service"; then
  echo "FAIL: launcher removes a required ART boot classpath" >&2
  exit 1
fi
if ! grep -Fq 'unset CLASSPATH;' "$service"; then
  echo "FAIL: expected CLASSPATH-only reset not found" >&2
  exit 1
fi
if ! grep -Fq 'app_process' "$service"; then
  echo "FAIL: expected app_process launcher reference not found" >&2
  exit 1
fi

echo "PASS: launcher preserves BOOTCLASSPATH and DEX2OATBOOTCLASSPATH"
