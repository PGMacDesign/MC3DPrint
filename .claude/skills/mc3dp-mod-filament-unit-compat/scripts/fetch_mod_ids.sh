#!/usr/bin/env bash
# fetch_mod_ids.sh — pull ground-truth item/block ids for a Minecraft mod straight from its
# GitHub source, so FU values are never registered against guessed (silently-dead) ids.
#
# Usage:
#   fetch_mod_ids.sh <owner/repo> <branch> [name-filter]
#
#   <owner/repo>   e.g. SlimeKnights/TinkersConstruct
#   <branch>       the MC-version branch, e.g. 1.20.1   (list with: gh api repos/<owner/repo>/branches)
#   [name-filter]  optional grep -i filter applied to the candidate-source-file list
#
# Output:
#   1. Every "<namespace>:<path>" id parsed from assets/*/lang/en_us.json (the obtainable set)
#   2. Candidate registration / recipe source files to read for acquisition context
#
# Requires: gh (authenticated), curl, jq. Reads only — makes no changes.
# Portable to macOS bash 3.2 (no mapfile / associative arrays).
set -uo pipefail

repo="${1:-}"; ref="${2:-}"; filter="${3:-}"
if [ -z "$repo" ] || [ -z "$ref" ]; then
  sed -n '2,18p' "$0" | sed -E 's/^# ?//'
  exit 2
fi
command -v gh  >/dev/null 2>&1 || { echo "ERROR: gh not found / not authenticated" >&2; exit 1; }
command -v jq  >/dev/null 2>&1 || { echo "ERROR: jq not found" >&2; exit 1; }
command -v curl>/dev/null 2>&1 || { echo "ERROR: curl not found" >&2; exit 1; }

echo "### mod: $repo @ $ref"

# --- 1. read the tree once into a temp file ----------------------------------------------
tree_json="$(gh api "repos/$repo/git/trees/$ref?recursive=1" 2>/dev/null || true)"
if [ -z "$tree_json" ] || [ "$(jq -r '.message // empty' <<<"$tree_json")" = "Not Found" ]; then
  echo "ERROR: could not read tree for $repo@$ref — wrong branch? try:" >&2
  echo "       gh api repos/$repo/branches --jq '.[].name'" >&2
  exit 1
fi
if [ "$(jq -r '.truncated // false' <<<"$tree_json")" = "true" ]; then
  echo "WARN: tree truncated (very large repo) — id list may be incomplete; verify key files manually." >&2
fi
paths_file="$(mktemp)"; trap 'rm -f "$paths_file" "$ids_tmp" 2>/dev/null' EXIT
jq -r '.tree[].path' <<<"$tree_json" > "$paths_file"

# --- 2. parse lang files into <namespace>:<path> ids -------------------------------------
echo
echo "### obtainable ids (from assets/*/lang/en_us.json)"
ids_tmp="$(mktemp)"
grep -E '(^|/)assets/[^/]+/lang/en_us\.json$' "$paths_file" 2>/dev/null | while IFS= read -r lp; do
  curl -fsSL "https://raw.githubusercontent.com/$repo/$ref/$lp" 2>/dev/null \
    | grep -oE '"(item|block)\.[a-z0-9_]+\.[a-z0-9_]+"' \
    | tr -d '"' \
    | sed -E 's/^(item|block)\.([a-z0-9_]+)\.([a-z0-9_]+)$/\2:\3/' >> "$ids_tmp"
done
if [ -s "$ids_tmp" ]; then
  sort -u "$ids_tmp" -o "$ids_tmp"
  cat "$ids_tmp"
  echo
  echo "# namespaces seen (gate id for ModList.isLoaded may differ from these item namespaces):"
  cut -d: -f1 "$ids_tmp" | sort -u | sed 's/^/  /'
else
  echo "(no ids parsed — no en_us.json, or unusual lang format; inspect registration classes directly)"
fi

# --- 3a. registration classes (.java) — read these for how each item is obtained ----------
echo
echo "### registration classes (.java) to read for acquisition context"
java_re='(TinkerMaterials|Materials|Items|Blocks|Registr|Registry|ModItems|ModBlocks|Content|Init)\.java$'
{ grep -E "$java_re" "$paths_file" 2>/dev/null || true; } \
  | { [ -n "$filter" ] && grep -i "$filter" || cat; } | sort -u | head -40

# --- 3b. machine-recipe data (json) — the custom recipes that DON'T derive ----------------
echo
echo "### machine-recipe data dirs (alloy/melting/casting/smeltery/inscriber — the leaves)"
{ grep -iE '(^|/)data/[^/]+/recipes?/.*(alloy|melting|casting|smeltery|induction|inscriber|fusion|pressing|mixing|crushing)' "$paths_file" 2>/dev/null || true; } \
  | grep -iv advancement | sed -E 's#/[^/]+\.json$##' | sort -u | head -25
echo
echo "# read a file with: curl -fsSL https://raw.githubusercontent.com/$repo/$ref/<path>"
