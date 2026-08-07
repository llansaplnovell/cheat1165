#!/usr/bin/env bash
#
# Пересобирает клиентский jar с новым пикером, кнопками copy/paste и цветом
# PlayerESP. Оригинал не трогается — результат пишется в отдельный файл.
#
#   ./patch-jar.sh primordial.jar primordial-alpha.jar
#
# Нужны: JDK 8+ (javac/java/javap), zip, curl (один раз, за ASM).
set -euo pipefail

IN=${1:?укажи исходный jar}
OUT=${2:-primordial-alpha.jar}
HERE=$(cd "$(dirname "$0")" && pwd)
SRC="$HERE/../src"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

ASM_VERSION=9.7.1
ASM_JAR="$HERE/asm-$ASM_VERSION.jar"
if [ ! -f "$ASM_JAR" ]; then
    echo ">> качаю ASM $ASM_VERSION"
    curl -sSLf -o "$ASM_JAR" \
        "https://repo1.maven.org/maven2/org/ow2/asm/asm/$ASM_VERSION/asm-$ASM_VERSION.jar"
fi

echo ">> компилирую классы клиента"
mkdir -p "$WORK/stage"
javac --release 8 -encoding UTF-8 -nowarn -proc:none \
    -cp "$IN" -d "$WORK/stage" $(find "$SRC" -name '*.java')

echo ">> патчу RendererLivingEntity (хук цвета для режима Minecraft)"
RLE=net/minecraft/client/renderer/entity/RendererLivingEntity.class
mkdir -p "$WORK/orig" "$WORK/stage/$(dirname "$RLE")"
unzip -q -o "$IN" "$RLE" -d "$WORK/orig"
javac -nowarn -proc:none -cp "$ASM_JAR" -d "$WORK" "$HERE/Patcher.java"
java -cp "$WORK:$ASM_JAR" Patcher "$WORK/orig/$RLE" "$WORK/stage/$RLE"

echo ">> собираю $OUT"
cp "$IN" "$OUT"
OUT_ABS=$(cd "$(dirname "$OUT")" && pwd)/$(basename "$OUT")
( cd "$WORK/stage" && zip -q "$OUT_ABS" $(find . -name '*.class' | sed 's|^\./||') )

echo ">> готово: $OUT"
