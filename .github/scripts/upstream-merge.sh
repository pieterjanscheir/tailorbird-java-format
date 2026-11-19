#!/usr/bin/env bash
# Merges an upstream palantir-java-format commit into the current branch, with upstream's code in Tailorbird's
# package and module names.
#
# Usage: upstream-merge.sh <upstream-commit> [commit message]
#
# Exit status:
#   0  merged and committed
#   1  conflicts: they are left in the working tree. Resolve them, then run `git commit --no-edit`.
#
# Tailorbird renamed every package and module (#27), so a plain `git merge` sees each renamed line as a Tailorbird
# change and conflicts wherever upstream edits next to one, such as an import or a project(':...') line.
# Instead, this renames upstream's side and the merge base with tailorbird-rename.sh and merges those, so both
# sides use the same names and only real conflicts remain. The merge commit's second parent is the upstream
# commit itself, so git and the sync workflow see it as merged.
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <upstream-commit> [commit message]" >&2
    exit 2
fi
sha=$(git rev-parse "$1^{commit}")
message=${2:-"Merge upstream palantir-java-format $1"}
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "The working tree has uncommitted changes. Commit or stash them first." >&2
    exit 2
fi

base=$(git merge-base HEAD "$sha")
tmp=$(mktemp -d)
trap 'git worktree remove --force "$tmp/tree" >/dev/null 2>&1 || true; rm -rf "$tmp"' EXIT

# Prints a commit that has the tree of commit $1 in Tailorbird's names.
renamed() {
    git worktree add --quiet --detach "$tmp/tree" "$1"
    (cd "$tmp/tree" && "$SCRIPT_DIR/tailorbird-rename.sh" >/dev/null)
    local tree
    tree=$(git -C "$tmp/tree" write-tree)
    git worktree remove --force "$tmp/tree"
    git commit-tree "$tree" -p "$1" -m "$1 in Tailorbird's names"
}

renamed_base=$(renamed "$base")
renamed_upstream=$(renamed "$sha")

status=0
# Conflict markers name the upstream ref instead of the renamed copy's hash.
export "GITHEAD_$renamed_upstream=$1"
git merge-recursive "$renamed_base" -- HEAD "$renamed_upstream" || status=$?
# Record the upstream commit, not its renamed copy, as the merged parent.
echo "$sha" >"$(git rev-parse --git-path MERGE_HEAD)"
echo "$message" >"$(git rev-parse --git-path MERGE_MSG)"
echo "no-ff" >"$(git rev-parse --git-path MERGE_MODE)"

if [ "$status" -ne 0 ]; then
    echo "Conflicts in:"
    git diff --name-only --diff-filter=U
    exit 1
fi
git commit --quiet --no-edit
echo "Merged $1 into $(git rev-parse --abbrev-ref HEAD)."
