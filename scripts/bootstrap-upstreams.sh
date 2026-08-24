#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/.." && pwd -P)"
lock_file="$script_dir/upstreams.lock"
upstream_root="${SUPER_ISLAND_UPSTREAMS_DIR:-$HOME/.cache/super-island/upstreams}"

fail() {
    printf 'bootstrap-upstreams: %s\n' "$*" >&2
    exit 1
}

mode="bootstrap"
if [[ "$#" -gt 1 ]]; then
    fail "usage: $0 [--check]"
fi
if [[ "$#" -eq 1 ]]; then
    [[ "$1" == "--check" ]] || fail "usage: $0 [--check]"
    mode="check"
fi

verify_build_pin() {
    local name="$1"
    local commit="$2"
    local build_file
    local pin_declaration

    case "$name" in
        KernelSU)
            build_file="$repo_root/modules/ui-design-system/build.gradle.kts"
            pin_declaration="val kernelSuReferenceCommit = \"$commit\""
            ;;
        HyperIsland)
            build_file="$repo_root/modules/hook-systemui/build.gradle.kts"
            pin_declaration="val hyperIslandCommit = \"$commit\""
            ;;
        HyperLyric)
            build_file="$repo_root/modules/hyperlyric-port/build.gradle.kts"
            pin_declaration="val hyperLyricReferenceCommit = \"$commit\""
            ;;
        *)
            fail "unsupported upstream name in lock file: $name"
            ;;
    esac

    grep -Fq -- "$pin_declaration" "$build_file" ||
        fail "$name commit is not pinned in ${build_file#"$repo_root/"}"
}

validate_lock_entry() {
    local name="$1"
    local repository="$2"
    local commit="$3"
    local relative_checkout="$4"
    local expected_checkout

    [[ "$name" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid upstream name: $name"
    [[ "$repository" == https://github.com/*.git ]] ||
        fail "$name repository must be a GitHub HTTPS URL"
    [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || fail "$name has an invalid commit: $commit"

    case "$name" in
        KernelSU)
            expected_checkout="KernelSU"
            kernel_su_entries=$((kernel_su_entries + 1))
            ;;
        HyperIsland)
            expected_checkout="HyperIsland"
            hyper_island_entries=$((hyper_island_entries + 1))
            ;;
        HyperLyric)
            expected_checkout="HyperLyric"
            hyper_lyric_entries=$((hyper_lyric_entries + 1))
            ;;
        *)
            fail "unsupported upstream name in lock file: $name"
            ;;
    esac

    [[ "$relative_checkout" == "$expected_checkout" ]] ||
        fail "$name checkout must be $expected_checkout"
    verify_build_pin "$name" "$commit"
}

prepare_upstream_root() {
    [[ ! -L "$upstream_root" ]] ||
        fail "upstream root must not be a symbolic link: $upstream_root"
    [[ ! -e "$upstream_root" || -d "$upstream_root" ]] ||
        fail "upstream root is not a directory: $upstream_root"

    if [[ ! -d "$upstream_root" ]]; then
        [[ "$mode" == "bootstrap" ]] ||
            fail "upstream checkout root is missing: $upstream_root"
        mkdir -p "$upstream_root"
    fi
}

verify_checkout() {
    local name="$1"
    local repository="$2"
    local commit="$3"
    local checkout="$4"
    local actual_repository
    local actual_commit
    local dirty

    [[ -e "$checkout/.git" ]] || fail "$name checkout is not a Git worktree: $checkout"

    actual_repository="$(git -C "$checkout" remote get-url origin)" ||
        fail "$name checkout has no origin remote: $checkout"
    [[ "$actual_repository" == "$repository" ]] ||
        fail "$name origin mismatch: expected $repository, found $actual_repository"

    actual_commit="$(git -C "$checkout" rev-parse HEAD)" ||
        fail "$name checkout has no readable HEAD: $checkout"
    [[ "$actual_commit" == "$commit" ]] ||
        fail "$name commit mismatch: expected $commit, found $actual_commit"

    dirty="$(git -C "$checkout" status --porcelain --untracked-files=all)" ||
        fail "$name checkout status could not be read: $checkout"
    [[ -z "$dirty" ]] ||
        fail "$name checkout has local changes; refusing to modify it"

    printf 'verified %-12s %s\n' "$name" "$commit"
}

bootstrap_checkout() {
    local name="$1"
    local repository="$2"
    local commit="$3"
    local relative_checkout="$4"
    local checkout="$upstream_root/$relative_checkout"

    [[ ! -L "$checkout" ]] || fail "$name checkout must not be a symbolic link: $checkout"
    if [[ -e "$checkout" ]]; then
        [[ -d "$checkout" ]] || fail "$name checkout path is not a directory: $checkout"
        verify_checkout "$name" "$repository" "$commit" "$checkout"
        return
    fi

    [[ "$mode" == "bootstrap" ]] ||
        fail "$name checkout is missing; run ./scripts/bootstrap-upstreams.sh"
    printf 'cloning  %-12s %s\n' "$name" "$repository"
    git clone --filter=blob:none --no-checkout -- "$repository" "$checkout"
    git -C "$checkout" checkout --detach "$commit"
    verify_checkout "$name" "$repository" "$commit" "$checkout"
}

[[ -f "$lock_file" ]] || fail "missing lock file: $lock_file"

entry_count=0
kernel_su_entries=0
hyper_island_entries=0
hyper_lyric_entries=0
names=()
repositories=()
commits=()
relative_checkouts=()
while IFS='|' read -r name repository commit relative_checkout extra; do
    [[ -z "$name" || "$name" == \#* ]] && continue
    [[ -z "${extra:-}" ]] || fail "too many fields in lock entry for $name"
    [[ -n "$repository" && -n "$commit" && -n "$relative_checkout" ]] ||
        fail "incomplete lock entry for $name"

    validate_lock_entry "$name" "$repository" "$commit" "$relative_checkout"
    names[$entry_count]="$name"
    repositories[$entry_count]="$repository"
    commits[$entry_count]="$commit"
    relative_checkouts[$entry_count]="$relative_checkout"
    entry_count=$((entry_count + 1))
done < "$lock_file"

[[ "$entry_count" -eq 3 ]] || fail "expected exactly 3 upstream entries, found $entry_count"
[[ "$kernel_su_entries" -eq 1 ]] ||
    fail "expected exactly one KernelSU entry, found $kernel_su_entries"
[[ "$hyper_island_entries" -eq 1 ]] ||
    fail "expected exactly one HyperIsland entry, found $hyper_island_entries"
[[ "$hyper_lyric_entries" -eq 1 ]] ||
    fail "expected exactly one HyperLyric entry, found $hyper_lyric_entries"

prepare_upstream_root
for ((index = 0; index < entry_count; index++)); do
    bootstrap_checkout \
        "${names[$index]}" \
        "${repositories[$index]}" \
        "${commits[$index]}" \
        "${relative_checkouts[$index]}"
done

printf 'All pinned upstream checkouts are ready.\n'
