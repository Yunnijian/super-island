#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/.." && pwd -P)"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/toolchains/jdk-21/Contents/Home}"

[[ -x "$JAVA_HOME/bin/java" ]] || {
    printf 'check: JDK 21 not found at %s\n' "$JAVA_HOME" >&2
    exit 1
}

cd "$repo_root"
exec ./gradlew \
    verifyMiuixPolicy \
    :core-model:test \
    :source-notification:testDebugUnitTest \
    :source-screenrecord:testDebugUnitTest \
    :hook-systemui:testDebugUnitTest \
    :app:testDebugUnitTest \
    :app:lintDebug \
    :app:assembleDebug \
    :hook-systemui:assembleDebug \
    :test-source:assembleDebug \
    :app:verifyBenchmarkXposedAbi
