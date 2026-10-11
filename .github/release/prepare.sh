#!/usr/bin/env bash
# Works out the version for a release and writes its notes.
#
#   prepare.sh patch|minor|major   Bump from the highest vX.Y.Z tag; notes cover tag..HEAD
#   prepare.sh recut               Reuse the highest tag as-is (rebuild after a failed run)
#
# The version lives only in the git tags and the notes only on the GitHub Release, so a
# release never commits to main (which only accepts pull requests). Notes are rebuilt from
# the commits between two tags, so a recut produces the same text as the original run.
set -euo pipefail

bump="${1:?usage: prepare.sh patch|minor|major|recut}"

# Conventional Commit type -> notes heading, in the order headings are printed.
types=(feat fix perf refactor docs test build ci chore revert)
declare -A headings=(
  [feat]="Features" [fix]="Fixes" [perf]="Performance" [refactor]="Refactors"
  [docs]="Docs" [test]="Tests" [build]="Build & CI" [ci]="Build & CI"
  [chore]="Chores" [revert]="Reverts"
)

output() {
  echo "$1=$2"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then echo "$1=$2" >>"$GITHUB_OUTPUT"; fi
}

release_tags() {
  git tag -l 'v*' --sort=-v:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' || true
}

git fetch --tags --force --quiet || true
latest_tag="$(release_tags | head -n1)"
latest="${latest_tag#v}"
latest="${latest:-0.0.0}"

if [[ "$bump" == "recut" ]]; then
  [[ -n "$latest_tag" ]] || { echo "::error::No release to re-cut yet; run patch, minor or major first." >&2; exit 1; }
  version="$latest"
  since="$(release_tags | sed -n 2p)"
  until="$latest_tag"
else
  IFS=. read -r major minor patch <<<"$latest"
  case "$bump" in
    major) version="$((major + 1)).0.0" ;;
    minor) version="${major}.$((minor + 1)).0" ;;
    patch) version="${major}.${minor}.$((patch + 1))" ;;
    *) echo "::error::bump must be patch, minor, major or recut (got '$bump')" >&2; exit 1 ;;
  esac
  since="$latest_tag"
  until="HEAD"
fi

mapfile -t subjects < <(git log "${since:+$since..}$until" --no-merges --pretty=format:%s)

declare -A grouped=()
for subject in "${subjects[@]}"; do
  if [[ "$subject" =~ ^([a-z]+)(\([^\)]*\))?!?:[[:space:]]*(.+)$ && -n "${headings[${BASH_REMATCH[1]}]:-}" ]]; then
    key="${headings[${BASH_REMATCH[1]}]}"
    line="${BASH_REMATCH[3]}"
  else
    key="Other"
    line="$subject"
  fi
  grouped[$key]+="- ${line}"$'\n'
done

notes="$(mktemp)"
{
  printed=""
  for t in "${types[@]}" other; do
    key="${headings[$t]:-Other}"
    [[ -n "${grouped[$key]:-}" && "$printed" != *"|$key|"* ]] || continue
    printed+="|$key|"
    printf '### %s\n\n%s\n' "$key" "${grouped[$key]}"
  done
  [[ -n "$printed" ]] || printf '*No changes recorded.*\n'
} >"$notes"

output version "$version"
output tag "v$version"
output notes_file "$notes"
