#!/usr/bin/env bash
# Generate grouped Markdown release notes from the commits between two refs.
#
#   release_notes.sh <prev-tag> <new-tag>
#
# Logs <prev-tag>..HEAD (the commits that will ship in <new-tag>), buckets them
# by Conventional-Commit type, and appends a GitHub compare link
# <prev-tag>...<new-tag>. Prints Markdown to stdout — redirect into a notes file
# or feed `gh release create --notes-file`.
#
# Plain POSIX-ish bash (no associative arrays) so it runs on macOS's bash 3.2.
set -euo pipefail

FROM="${1:?usage: release_notes.sh <prev-tag> <new-tag>}"
NEW="${2:?usage: release_notes.sh <prev-tag> <new-tag>}"
REPO="PGMacDesign/MC3DPrint"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

while IFS= read -r subject; do
  [ -z "$subject" ] && continue
  if [[ "$subject" =~ ^([a-z]+)(\([^\)]*\))?!?:[[:space:]](.*)$ ]]; then
    type="${BASH_REMATCH[1]}"
    scope="${BASH_REMATCH[2]//[()]/}"
    msg="${BASH_REMATCH[3]}"
  else
    type="other"; scope=""; msg="$subject"
  fi
  case "$type" in
    feat|fix|perf|refactor|docs|test|build|ci|chore) ;;
    *) type="other" ;;
  esac
  if [ -n "$scope" ]; then
    printf -- '- **%s**: %s\n' "$scope" "$msg" >> "$tmp/$type"
  else
    printf -- '- %s\n' "$msg" >> "$tmp/$type"
  fi
done < <(git log --no-merges --pretty=format:'%s' "${FROM}..HEAD")

emit() {  # $1 = type bucket, $2 = section heading
  if [ -s "$tmp/$1" ]; then
    printf '### %s\n\n' "$2"
    cat "$tmp/$1"
    printf '\n'
  fi
}

emit feat     "✨ Features"
emit fix      "🐛 Fixes"
emit perf     "⚡ Performance"
emit refactor "♻️ Refactors"
emit docs     "📝 Docs"
emit test     "✅ Tests"
emit build    "📦 Build"
emit ci       "🤖 CI"
emit chore    "🔧 Chores"
emit other    "📌 Other"

printf '**Full changelog:** https://github.com/%s/compare/%s...%s\n' "$REPO" "$FROM" "$NEW"
