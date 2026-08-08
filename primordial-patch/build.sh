#!/usr/bin/env bash
# Compiles the patched client classes against an existing primordial jar and writes a new jar
# with those classes replaced/added. Everything else in the jar is left untouched.
#
#   ./build.sh <input.jar> [output.jar]
#
# Requires a JDK (javac able to target 8) and zip.
set -euo pipefail

IN_JAR="$(realpath "${1:?usage: build.sh <input.jar> [output.jar]}")"
OUT_JAR="${2:-${IN_JAR%.jar}-skypvp.jar}"
# the injection step runs from the class output dir, so the target has to be absolute
OUT_JAR="$(realpath -m "$OUT_JAR")"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$HERE/src"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

echo "[1/3] compiling"
find "$SRC" -name '*.java' > "$BUILD/sources.txt"
javac -nowarn -encoding UTF-8 -source 8 -target 8 \
    -classpath "$IN_JAR" -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "[2/3] copying jar"
cp "$IN_JAR" "$OUT_JAR"

echo "[3/3] injecting classes"
(cd "$BUILD/classes" && find . -name '*.class' -printf '%P\n' | zip -q -X "$OUT_JAR" -@)

echo "done -> $OUT_JAR"
