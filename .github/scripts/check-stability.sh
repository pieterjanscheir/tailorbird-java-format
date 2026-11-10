#!/usr/bin/env bash
# Formats real-world Java projects twice with each preset, and fails if the second run changes anything or the
# formatter reports an error on a file. One run must be enough: see issue #48 (and #40, which this would have caught).
#
# Usage: check-stability.sh <formatter launcher> <work folder> [preset ...]
# The projects, pinned to fixed commits, are listed in stability-projects.txt next to this script.
set -euo pipefail

formatter=$1
work=$2
shift 2
presets=("$@")
if [ ${#presets[@]} -eq 0 ]; then
    presets=(bowerbird palantir google)
fi

script_dir=$(cd "$(dirname "$0")" && pwd)
# The launcher on main needs these; the bowerbird launcher passes them itself, and passing them twice is harmless.
JAVA_OPTS="$(for p in api code file main parser tree util; do
    printf -- '--add-exports=jdk.compiler/com.sun.tools.javac.%s=ALL-UNNAMED ' "$p"
done)"
export JAVA_OPTS

mkdir -p "$work/projects"
failed=0

# Formats every Java file under $1 in place with the settings file $2, in batches. Returns nonzero on any error.
format_tree() {
    (cd "$1" && find . -name '*.java' -print0 | sort -z |
        xargs -0 -n 400 "$formatter" --config "$2" --replace)
}

repo_root=$(cd "$script_dir/../.." && pwd)

while read -r name url commit; do
    case "$name" in '' | '#'*) continue ;; esac
    project="$work/projects/$name"
    if [ "$url" = local ]; then
        # A folder in this repository whose *.input files are test inputs: copy them in as *.java.
        rm -rf "$project"
        (cd "$repo_root/$commit" && find . -name '*.input') | while read -r input; do
            mkdir -p "$project/$(dirname "$input")"
            cp "$repo_root/$commit/$input" "$project/${input%.input}.java"
        done
    elif [ ! -d "$project" ]; then
        git init -q "$project"
        git -C "$project" fetch -q --depth 1 "$url" "$commit"
        git -C "$project" -c advice.detachedHead=false checkout -q FETCH_HEAD
    fi
    files=$(cd "$project" && find . -name '*.java' | wc -l | tr -d ' ')

    for preset in "${presets[@]}"; do
        config="$work/$preset.yml"
        printf 'preset: %s\n' "$preset" > "$config"
        first="$work/runs/$name-$preset-1"
        second="$work/runs/$name-$preset-2"
        rm -rf "$first" "$second"
        mkdir -p "$work/runs"
        # Only the Java files are copied, so the comparison below sees nothing else.
        (cd "$project" && find . -name '*.java' -print0 | cpio -0pdm --quiet "$first")

        start=$SECONDS
        if ! errors=$(format_tree "$first" "$config" 2>&1); then
            echo "::error::$name ($preset): the formatter reported errors on the first run"
            printf '%s\n' "$errors" | head -40
            failed=1
            continue
        fi
        cp -R "$first" "$second"
        if ! errors=$(format_tree "$second" "$config" 2>&1); then
            echo "::error::$name ($preset): the formatter reported errors on the second run"
            printf '%s\n' "$errors" | head -40
            failed=1
            continue
        fi

        # Files the second run changed, relative to the project, minus the known ones in stability-known.txt.
        changed=$(cd "$first" && find . -name '*.java' | sed 's|^\./||' | sort | while read -r file; do
            cmp -s "$first/$file" "$second/$file" || echo "$file"
        done)
        known=$(awk -v n="$name" -v p="$preset" '$1 == n && $2 == p { print $3 }' "$script_dir/stability-known.txt")
        unexpected=$(comm -23 <(printf '%s\n' "$changed" | sed '/^$/d') <(printf '%s\n' "$known" | sed '/^$/d' | sort))
        for file in $known; do
            if printf '%s\n' "$changed" | grep -qxF "$file"; then
                echo "::warning::$name ($preset): $file is still unstable (known, in stability-known.txt)"
            else
                echo "::warning::$name ($preset): $file is stable now; remove it from stability-known.txt"
            fi
        done
        if [ -z "$unexpected" ]; then
            echo "$name ($preset): $files files stable ($((SECONDS - start))s)"
        else
            echo "::error::$name ($preset): the second run changed $(printf '%s\n' "$unexpected" | wc -l | tr -d ' ') of $files files"
            for file in $unexpected; do
                diff -u "$first/$file" "$second/$file" --label "$file (first run)" --label "$file (second run)" | head -60 || true
            done
            failed=1
        fi
    done
done < "$script_dir/stability-projects.txt"

exit $failed
