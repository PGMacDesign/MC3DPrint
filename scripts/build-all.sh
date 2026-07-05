#!/usr/bin/env bash
#
# build-all.sh — build every shippable MC3DPrint jar in one shot, into ./dist/.
#
# Produces:
#   dist/mc3dprint-<ver>-neoforge-1.21.1.jar   (Stonecutter node, this tree, Java 21)
#   dist/mc3dprint-<ver>-neoforge-1.21.8.jar   (Stonecutter node, this tree, Java 21)
#   dist/mc3dprint-<ver>-forge-1.20.1.jar      (legacy/1.20.1 branch, Forge, Java 17)
#
# The NeoForge jars come from the multi-version Stonecutter tree (one source, one jar
# per node). The 1.20.1 Forge jar lives on the separate `legacy/1.20.1` branch and is
# built in a throwaway git worktree so the working tree is never disturbed.
#
# Usage:
#   ./scripts/build-all.sh                       # version from gradle.properties
#   ./scripts/build-all.sh --version 0.11.0      # override version (e.g. a release tag)
#   ./scripts/build-all.sh --neoforge-only       # skip the legacy Forge build
#
# JDKs: NeoForge needs a Java 21 launcher (Stonecutter requires it); legacy Forge needs
# Java 17. Auto-detected, or set MC3DP_JDK21 / MC3DP_JDK17 to override. In CI, GitHub's
# setup-java exports JAVA_HOME_21_X64 / JAVA_HOME_17_X64, which are picked up automatically.
#
# Designed to drop straight into a GitHub Action (see .github/workflows/release-all.yml).
set -euo pipefail

# --- locate repo root (script lives in scripts/) ---
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# --- the NeoForge nodes built from THIS tree. Add future versions here (e.g. "1.21.8 26.2"). ---
NEOFORGE_NODES=("1.21.1" "1.21.8" "1.21.9" "1.21.10" "1.21.11")
LEGACY_BRANCH="legacy/1.20.1"
LEGACY_MC="1.20.1"
CANONICAL_NODE="1.21.1"   # the vcsVersion; the tree is left on this on exit

# --- args ---
VERSION=""
NEOFORGE_ONLY=0
while [ $# -gt 0 ]; do
    case "$1" in
        --version) VERSION="$2"; shift 2 ;;
        --version=*) VERSION="${1#*=}"; shift ;;
        --neoforge-only) NEOFORGE_ONLY=1; shift ;;
        -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done
if [ -z "$VERSION" ]; then
    VERSION="$(grep -E '^mod_version=' gradle.properties | head -1 | cut -d= -f2 | tr -d '[:space:]')"
fi
[ -n "$VERSION" ] || { echo "could not determine version" >&2; exit 1; }

# --- resolve a JDK home for a given major version ---
# order: explicit override -> GitHub setup-java env -> macOS java_home -> foojay (~/.gradle/jdks) -> empty
resolve_jdk() {
    local major="$1" override_var="$2" candidate
    candidate="${!override_var:-}"
    [ -n "$candidate" ] && { echo "$candidate"; return; }
    candidate="$(printenv "JAVA_HOME_${major}_X64" 2>/dev/null || true)"
    [ -n "$candidate" ] && { echo "$candidate"; return; }
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
        candidate="$(/usr/libexec/java_home -v "$major" 2>/dev/null || true)"
        [ -n "$candidate" ] && { echo "$candidate"; return; }
    fi
    candidate="$(ls -d "$HOME"/.gradle/jdks/*"${major}"*/ 2>/dev/null | head -1 || true)"
    if [ -n "$candidate" ]; then
        # foojay may nest jdk-XX/Contents/Home (macOS) or jdk-XX (linux)
        [ -d "${candidate}Contents/Home" ] && candidate="${candidate}Contents/Home"
        local inner; inner="$(ls -d "${candidate}"jdk-* 2>/dev/null | head -1 || true)"
        [ -n "$inner" ] && { [ -d "${inner}/Contents/Home" ] && echo "${inner}/Contents/Home" || echo "$inner"; return; }
        echo "${candidate%/}"; return
    fi
    echo ""
}

JDK21="$(resolve_jdk 21 MC3DP_JDK21)"
[ -n "$JDK21" ] && [ -x "$JDK21/bin/java" ] || { echo "ERROR: no Java 21 JDK found (set MC3DP_JDK21)" >&2; exit 1; }
echo "Java 21 (NeoForge): $JDK21"

JDK17="$(resolve_jdk 17 MC3DP_JDK17)"
if [ "$NEOFORGE_ONLY" -eq 0 ]; then
    if [ -n "$JDK17" ] && [ -x "$JDK17/bin/java" ]; then
        echo "Java 17 (Forge):    $JDK17"
    else
        echo "WARNING: no Java 17 JDK found — skipping the $LEGACY_MC Forge build (set MC3DP_JDK17 to enable)." >&2
        NEOFORGE_ONLY=1
    fi
fi

DIST="$ROOT/dist"
rm -rf "$DIST"; mkdir -p "$DIST"
echo "==> Building MC3DPrint $VERSION → $DIST"

# leave the tree on the canonical node no matter how we exit
restore_node() { JAVA_HOME="$JDK21" ./gradlew "Set active project to $CANONICAL_NODE" -q >/dev/null 2>&1 || true; }
trap restore_node EXIT

# pick the production jar in a build/libs dir (exclude sources/dev/javadoc)
pick_jar() {
    ls "$1"/mc3dprint-*.jar 2>/dev/null | grep -vE -- '-(sources|dev|javadoc|slim)\.jar$' | head -1
}

# --- NeoForge nodes (this tree) ---
chmod +x ./gradlew
for node in "${NEOFORGE_NODES[@]}"; do
    echo "==> NeoForge $node"
    JAVA_HOME="$JDK21" ./gradlew "Set active project to $node" -q
    JAVA_HOME="$JDK21" ./gradlew ":$node:assemble" -Pmod_version="$VERSION" --no-daemon --stacktrace
    jar="$(pick_jar "versions/$node/build/libs")"
    [ -n "$jar" ] || { echo "ERROR: no jar produced for node $node" >&2; exit 1; }
    cp "$jar" "$DIST/mc3dprint-$VERSION-neoforge-$node.jar"
done

# --- legacy 1.20.1 Forge (separate branch, throwaway worktree, Java 17) ---
if [ "$NEOFORGE_ONLY" -eq 0 ]; then
    echo "==> Forge $LEGACY_MC (from $LEGACY_BRANCH)"
    # Resolve the branch locally (dev) or as a remote-tracking ref (CI shallow checkout).
    LEGACY_REF="$LEGACY_BRANCH"
    git rev-parse --verify --quiet "$LEGACY_REF^{commit}" >/dev/null || LEGACY_REF="origin/$LEGACY_BRANCH"
    git rev-parse --verify --quiet "$LEGACY_REF^{commit}" >/dev/null \
        || { echo "ERROR: $LEGACY_BRANCH not found (need a full-history checkout: fetch-depth 0)" >&2; exit 1; }
    git worktree prune
    WT="$ROOT/.worktrees/legacy-1.20.1"
    rm -rf "$WT"
    git worktree add --force --detach "$WT" "$LEGACY_REF"
    (
        cd "$WT"
        chmod +x ./gradlew
        JAVA_HOME="$JDK17" ./gradlew build -Pmod_version="$VERSION" --no-daemon --stacktrace
    )
    jar="$(pick_jar "$WT/build/libs")"
    [ -n "$jar" ] || { echo "ERROR: no jar produced for $LEGACY_MC" >&2; git worktree remove --force "$WT"; exit 1; }
    cp "$jar" "$DIST/mc3dprint-$VERSION-forge-$LEGACY_MC.jar"
    git worktree remove --force "$WT"
fi

echo
echo "==> Done. Artifacts in $DIST:"
ls -la "$DIST"/*.jar | awk '{print "    " $NF, "(" $5 " bytes)"}'
