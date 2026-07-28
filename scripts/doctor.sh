#!/usr/bin/env bash

set -euo pipefail

default_jdk="$HOME/.local/share/toolchains/jdk-21/Contents/Home"
java_home="${JAVA_HOME:-$default_jdk}"
android_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
upstream_root="${SUPER_ISLAND_UPSTREAMS_DIR:-$HOME/.cache/super-island/upstreams}"
lab_root="${SUPER_ISLAND_LAB_DIR:-$HOME/Developer/super-island-lab}"

fail() {
    printf 'doctor: %s\n' "$*" >&2
    exit 1
}

[[ -x "$java_home/bin/java" ]] || fail "JDK 21 not found at $java_home"
[[ -n "$android_home" ]] || fail "ANDROID_HOME or ANDROID_SDK_ROOT is not set"
[[ -d "$android_home" ]] || fail "Android SDK not found at $android_home"
[[ -d "$upstream_root/KernelSU/.git" ]] || fail "KernelSU checkout is missing"
[[ -d "$upstream_root/HyperIsland/.git" ]] || fail "HyperIsland checkout is missing"
[[ -d "$lab_root" ]] || fail "external lab directory is missing at $lab_root"

for command_name in git adb; do
    command -v "$command_name" >/dev/null || fail "$command_name is not available on PATH"
done

java_version="$($java_home/bin/java -version 2>&1 | head -n 1)"
adb_version="$(adb version | head -n 1)"
broken_links="$(find "$HOME/.local/bin" -maxdepth 1 -type l ! -exec test -e {} \; -print 2>/dev/null || true)"
[[ -z "$broken_links" ]] || fail "broken links found in ~/.local/bin:\n$broken_links"

printf 'JDK:       %s\n' "$java_version"
printf 'Android:   %s\n' "$android_home"
printf 'ADB:       %s\n' "$adb_version"
printf 'Upstreams: %s\n' "$upstream_root"
printf 'Lab:       %s\n' "$lab_root"
printf 'doctor: environment is ready\n'
