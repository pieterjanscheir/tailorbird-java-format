#!/usr/bin/env bash
# Merges a palantir-java-format release into a branch and opens a pull request for it.
#
# Usage: upstream-sync.sh [upstream-ref]
#   upstream-ref  an upstream tag or branch; defaults to the latest release tag (x.y.z)
#
# Environment:
#   DRY_RUN=1      do everything locally, but push nothing and create no pull request or issue
#   SKIP_FORMAT=1  don't run ./gradlew format after renaming (for quick local tests; CI will then flag formatting)
#   BASE_BRANCH    the branch to merge into; defaults to main (overridable for testing)
#   GH_TOKEN       needed (unless DRY_RUN=1) for the gh CLI
#
# Outcomes:
#   - already merged, or a pull request or issue already open for it: nothing happens
#   - merges cleanly: pushes branch upstream-sync/<ref>, opens a pull request into main, starts CI on it
#   - conflicts: opens an issue listing the conflicting files
#
# Tailorbird renamed upstream's packages and modules (#27), so the merge goes through upstream-merge.sh, which
# merges upstream's code in Tailorbird's names.
set -euo pipefail

UPSTREAM_URL=${UPSTREAM_URL:-https://github.com/palantir/palantir-java-format.git}
UPSTREAM_REMOTE=${UPSTREAM_REMOTE:-upstream} # overridable for testing against another repository
BASE_BRANCH=${BASE_BRANCH:-main}
DRY_RUN=${DRY_RUN:-0}
SKIP_FORMAT=${SKIP_FORMAT:-0}
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

run() {
    if [ "$DRY_RUN" = 1 ]; then
        echo "[dry run] $*"
    else
        "$@"
    fi
}

git remote get-url "$UPSTREAM_REMOTE" >/dev/null 2>&1 || git remote add "$UPSTREAM_REMOTE" "$UPSTREAM_URL"
# Upstream tags go into their own namespace so they never mix with Tailorbird's own release tags.
git fetch --quiet --no-tags "$UPSTREAM_REMOTE" \
    "+refs/tags/*:refs/upstream-tags/*" "+refs/heads/*:refs/remotes/upstream/*"

ref=${1:-}
if [ -z "$ref" ]; then
    ref=$(git for-each-ref --format='%(refname:strip=2)' refs/upstream-tags \
        | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1)
fi
if git rev-parse --verify --quiet "refs/upstream-tags/$ref^{commit}" >/dev/null; then
    sha=$(git rev-parse "refs/upstream-tags/$ref^{commit}")
elif git rev-parse --verify --quiet "refs/remotes/upstream/$ref^{commit}" >/dev/null; then
    sha=$(git rev-parse "refs/remotes/upstream/$ref^{commit}")
else
    echo "::error::Unknown upstream tag or branch: $ref"
    exit 1
fi
echo "Upstream $ref is $sha"

git fetch --quiet origin "$BASE_BRANCH"
if git merge-base --is-ancestor "$sha" "origin/$BASE_BRANCH"; then
    echo "Upstream $ref is already merged into $BASE_BRANCH. Nothing to do."
    exit 0
fi

branch="upstream-sync/$ref"
title="Merge upstream palantir-java-format $ref"
conflict_title="Upstream palantir-java-format $ref has merge conflicts"

if [ "$DRY_RUN" != 1 ]; then
    if [ -n "$(gh pr list --head "$branch" --state open --json number --jq '.[].number')" ]; then
        echo "A pull request for $branch is already open. Nothing to do."
        exit 0
    fi
    if [ -n "$(gh issue list --state open --search "\"$conflict_title\" in:title" --json number --jq '.[].number')" ]; then
        echo "An issue about conflicts with $ref is already open. Nothing to do."
        exit 0
    fi
fi

git switch --quiet -C "$branch" "origin/$BASE_BRANCH"
merge_status=0
"$SCRIPT_DIR/upstream-merge.sh" "$sha" "$title" >/dev/null || merge_status=$?
if [ "$merge_status" -gt 1 ]; then
    echo "::error::upstream-merge.sh failed."
    exit "$merge_status"
elif [ "$merge_status" -eq 0 ]; then
    echo "Merged $ref cleanly into $branch."

    # Renamed code can need re-wrapping, such as lines that got longer with the longer package name.
    if [ "$SKIP_FORMAT" != 1 ]; then
        ./gradlew format --console=plain --quiet
        git add -A
        if ! git diff --cached --quiet; then
            git commit --quiet -m "Format upstream $ref after renaming"
            echo "Formatted upstream changes."
        fi
    fi
    # Only the SPI package and the forwarding Main may keep upstream's package (see tailorbird-rename.sh).
    leftovers=$(git ls-files \
        | grep -e '^palantir-java-format' -e '^gradle-palantir-java-format' -e '/com/palantir/javaformat/' \
        | grep -vE '^tailorbird-java-format-spi/src/main/java/com/palantir/javaformat/java/[^/]+$' \
        | grep -vxF 'tailorbird-java-format/src/main/java/com/palantir/javaformat/java/Main.java' || true)
    if [ -n "$leftovers" ]; then
        echo "::error::Files are still under upstream's names after renaming:"
        echo "$leftovers"
        exit 1
    fi

    run git push --force-with-lease origin "$branch"
    body=$(cat <<EOF
Merges upstream [palantir-java-format $ref](https://github.com/palantir/palantir-java-format/releases/tag/$ref) into \`$BASE_BRANCH\`.

- Upstream changes: https://github.com/palantir/palantir-java-format/compare/$(git merge-base "$sha" "origin/$BASE_BRANCH")...$sha
- The merge had no conflicts. Upstream's code was merged in Tailorbird's package and module names (\`upstream-merge.sh\`).
- CI shows whether the Tailorbird rules still work with these changes.

Opened by the upstream sync workflow.
EOF
)
    run gh pr create --base "$BASE_BRANCH" --head "$branch" --title "$title" --body "$body"
    # Pull requests opened with the workflow token do not trigger other workflows, so start CI explicitly.
    run gh workflow run ci.yml --ref "$branch"
else
    conflicts=$(git diff --name-only --diff-filter=U)
    git merge --abort
    echo "Merging $ref conflicts in:"
    echo "$conflicts"
    body=$(cat <<EOF
Merging upstream [palantir-java-format $ref](https://github.com/palantir/palantir-java-format/releases/tag/$ref) into \`$BASE_BRANCH\` has conflicts in:

$(echo "$conflicts" | sed 's/^/- `/; s/$/`/')

To resolve:

\`\`\`bash
git fetch upstream --tags
git switch -c $branch origin/$BASE_BRANCH
.github/scripts/upstream-merge.sh $sha
# resolve the conflicts (see "Resolving upstream conflicts" in README.md), then:
git add -A && git commit --no-edit
./gradlew format # then commit what it changes, if anything
# push, and open a pull request
\`\`\`

Opened by the upstream sync workflow.
EOF
)
    run gh issue create --title "$conflict_title" --body "$body"
fi
