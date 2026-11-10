#!/usr/bin/env bash
# Checks that IntelliJ IDEA, with the code style Tailorbird exports, leaves Tailorbird's output unchanged.
#
# For every case in tailorbird-testdata, it exports the case's settings (its .yml, or the defaults) twice: as an
# IntelliJ code style XML and as an .editorconfig. Each time it runs IntelliJ's command-line formatter on the case's
# expected output and fails if IntelliJ changes it. Cases whose export says a setting is not expressible are skipped.
#
# Usage: IDEA_HOME=/path/to/intellij .github/scripts/intellij-style-check.sh [case...]
# With case names (such as Javadoc), only those cases are checked.
# Build the launcher first: ./gradlew :tailorbird-java-format:installDist
set -euo pipefail

repo="$(cd "$(dirname "$0")/../.." && pwd)"
testdata="$repo/tailorbird-java-format/src/test/resources/io/github/pieterjanscheir/tailorbird/java/tailorbird-testdata"
tailorbird="$repo/tailorbird-java-format/build/install/tailorbird-java-format/bin/tailorbird-java-format"

if [ -z "${IDEA_HOME:-}" ]; then
    echo "Set IDEA_HOME to an IntelliJ IDEA installation." >&2
    exit 2
fi
format="$IDEA_HOME/bin/format.sh"
if [ ! -x "$format" ]; then
    # macOS app bundles keep bin/ under Contents/.
    format="$IDEA_HOME/Contents/bin/format.sh"
fi
if [ ! -x "$tailorbird" ]; then
    echo "Build the launcher first: ./gradlew :tailorbird-java-format:installDist" >&2
    exit 2
fi

work="$(mktemp -d)"
# Keep the exit status: the cleanup must not turn an error into success.
trap 'status=$?; rm -rf "$work"; exit $status' EXIT

# Separate config and system folders, so the check also runs while the IDE is open.
mkdir -p "$work/idea/config" "$work/idea/system" "$work/idea/log" "$work/idea/plugins"
cat > "$work/idea.properties" <<EOF
idea.config.path=$work/idea/config
idea.system.path=$work/idea/system
idea.log.path=$work/idea/log
idea.plugins.path=$work/idea/plugins
EOF
export IDEA_PROPERTIES="$work/idea.properties"
export JAVA_OPTS="$(for p in api code file parser tree util; do printf -- '--add-exports=jdk.compiler/com.sun.tools.javac.%s=ALL-UNNAMED ' "$p"; done)"

failed=0
for mode in intellij-style editorconfig; do
    for input in "$testdata"/*.input; do
        case="$(basename "$input" .input)"
        if [ $# -gt 0 ] && [[ " $* " != *" $case "* ]]; then
            continue
        fi
        dir="$work/$mode/$case"
        mkdir -p "$dir/formatted"
        config=()
        if [ -f "$testdata/$case.yml" ]; then
            config=(--config "$testdata/$case.yml")
        fi
        # ${config[@]+...} keeps an empty array working in bash 3.2 (macOS) with set -u.
        "$tailorbird" ${config[@]+"${config[@]}"} "--export-$mode" > "$dir/export"
        if grep -q "Not expressible" "$dir/export"; then
            echo "SKIP  $mode $case: $(grep -o 'Not expressible: [^:]*' "$dir/export" | head -1)"
            continue
        fi

        cp "$testdata/$case.output" "$dir/formatted/$case.java"
        if [ "$mode" = editorconfig ]; then
            { echo "root = true"; cat "$dir/export"; } > "$dir/formatted/.editorconfig"
            args=(-allowDefaults)
        else
            args=(-s "$dir/export")
        fi
        if ! "$format" "${args[@]}" -m '*.java' -r "$dir/formatted" > "$dir/format.log" 2>&1; then
            echo "FAIL  $mode $case: IntelliJ's formatter failed"
            cat "$dir/format.log"
            failed=1
            continue
        fi

        if diff -u "$testdata/$case.output" "$dir/formatted/$case.java" > "$dir/diff"; then
            echo "OK    $mode $case"
        else
            echo "FAIL  $mode $case: IntelliJ changes Tailorbird's output (- Tailorbird, + IntelliJ)"
            cat "$dir/diff"
            failed=1
        fi
    done
done
exit "$failed"
