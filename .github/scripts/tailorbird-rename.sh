#!/usr/bin/env bash
# Applies Tailorbird's package and module rename to upstream code that arrived under the old names.
#
# Usage: tailorbird-rename.sh
#   Run from the repository root after merging upstream palantir-java-format (upstream-sync.sh does this).
#   It is idempotent: on a tree that already uses Tailorbird's names it changes nothing.
#
# What it does:
#   1. Moves files that are still in an old module folder (palantir-java-format*, gradle-palantir-java-format)
#      or under com/palantir/javaformat to their Tailorbird location. That covers files upstream adds.
#   2. Rewrites com.palantir.javaformat to io.github.pieterjanscheir.tailorbird, and old Gradle project paths and
#      module folders to the tailorbird-java-format* names, in every tracked text file.
#   3. Adds imports for SPI types to classes in io.github.pieterjanscheir.tailorbird.java, which used them from
#      the same package before.
#
# Kept as is (the upstream Gradle and IntelliJ plugins load Tailorbird through these names, see #33):
#   - the SPI: everything in tailorbird-java-format-spi/src/main/java/com/palantir/javaformat/java/
#   - the forwarding com.palantir.javaformat.java.Main in tailorbird-java-format
#   - Maven coordinates (com.palantir.javaformat:...), URLs of the upstream repository, and the files below
set -euo pipefail

SPI_DIR=tailorbird-java-format-spi/src/main/java/com/palantir/javaformat/java
SHIM=tailorbird-java-format/src/main/java/com/palantir/javaformat/java/Main.java
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

# Historical or upstream-only files, and test data, which never use the package.
excluded() {
    case "$1" in
        NOTICE | versions.lock | .palantir/revapi.yml | docs/upstream-README.md) return 0 ;;
        .github/scripts/*) return 0 ;; # Tailorbird's own scripts, which name the old paths on purpose
        # Tailorbird's own docs, which name the kept SPI and Main on purpose.
        README.md | docs/*) return 0 ;;
        "$SPI_DIR"/* | "$SHIM") return 0 ;;
        *.input | *.output | *.png | *.jpg | *.gif | *.ico | *.jar | *.svg) return 0 ;;
    esac
    return 1
}

move() {
    mkdir -p "$(dirname "$2")"
    git mv "$1" "$2"
    echo "moved $1 -> $2"
}

# 1. Move files.
git ls-files | while IFS= read -r f; do
    to=$(printf '%s\n' "$f" | sed -E 's#^(gradle-)?palantir-java-format#\1tailorbird-java-format#')
    if [ "$to" != "$f" ]; then move "$f" "$to"; f=$to; fi
    case "$f" in "$SPI_DIR"/* | "$SHIM") continue ;; esac
    to=$(printf '%s\n' "$f" | sed -E 's#(^|/)com/palantir/javaformat/#\1io/github/pieterjanscheir/tailorbird/#')
    if [ "$to" != "$f" ]; then move "$f" "$to"; fi
done

# Symlinks point at old paths too; fix their targets.
git ls-files -s | awk '$1 == "120000" { print $4 }' | while IFS= read -r link; do
    target=$(readlink "$link")
    new=$(printf '%s\n' "$target" | perl -pe 's#(?<![\w.-])(gradle-)?palantir-java-format(-[a-z-]+)?/#$1tailorbird-java-format$2/#g; s#com/palantir/javaformat/#io/github/pieterjanscheir/tailorbird/#g')
    if [ "$new" != "$target" ]; then
        ln -sfn "$new" "$link"
        git add "$link"
        echo "relinked $link -> $new"
    fi
done

# 2. and 3. Rewrite contents. The SPI types are whatever the SPI package contains.
spi_types=$(find "$SPI_DIR" -maxdepth 1 -name '*.java' -exec basename {} .java \; | sort | paste -sd '|' -)
# Module suffixes, from the module folders: "|-spi|-jdk-bootstrap|...".
modules=$(find . -maxdepth 1 -type d \( -name 'tailorbird-java-format*' -o -name 'gradle-tailorbird-java-format*' \) \
    | sed -E 's#^\./(gradle-)?tailorbird-java-format##' | sort -u | paste -sd '|' -)
git ls-files | while IFS= read -r f; do
    if [ ! -f "$f" ] || [ -L "$f" ] || excluded "$f"; then continue; fi
    if ! grep -qE 'com[./]palantir[./]javaformat|palantir-java-format|^package io\.github\.pieterjanscheir\.tailorbird\.java;' "$f"; then continue; fi
    FILE=$f SPI_TYPES=$spi_types MODULES=$modules perl -i "$SCRIPT_DIR/tailorbird-rename.pl" "$f"
done
git add -A
