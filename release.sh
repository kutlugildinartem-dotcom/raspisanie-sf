#!/usr/bin/env bash
# Выпуск новой версии: поднимает версию, собирает подписанный APK, считает
# бинарный патч от предыдущего релиза и публикует всё на GitHub.
#
#   ./release.sh 1.1 "Что изменилось"
#
# versionCode обязан расти: Android не ставит поверх сборку с тем же или
# меньшим кодом, даже если versionName новее.
#
# Патч — необязательный шаг. Если предыдущего релиза нет, или скачать его
# не вышло, или сама генерация патча упала — публикуется обычный полный APK,
# как раньше. Свежеустановленное приложение при следующем запуске увидит
# либо патч (если оно ровно на предыдущей версии), либо полный APK.
set -euo pipefail

VERSION="${1:-}"
NOTES="${2:-}"

if [[ -z "$VERSION" ]]; then
    echo "Использование: ./release.sh <версия> [описание]" >&2
    echo "Например:      ./release.sh 1.1 \"Починил листание в виджете\"" >&2
    exit 1
fi

GRADLE_FILE="app/build.gradle.kts"
CURRENT_CODE=$(grep -oE 'versionCode = [0-9]+' "$GRADLE_FILE" | grep -oE '[0-9]+')
NEXT_CODE=$((CURRENT_CODE + 1))

echo "==> Версия $VERSION (versionCode $CURRENT_CODE -> $NEXT_CODE)"
sed -i "s|versionCode = $CURRENT_CODE|versionCode = $NEXT_CODE|" "$GRADLE_FILE"
sed -i "s|versionName = \"[^\"]*\"|versionName = \"$VERSION\"|" "$GRADLE_FILE"

echo "==> Тесты"
./gradlew :app:testDebugUnitTest --quiet

echo "==> Сборка подписанного APK"
./gradlew :app:assembleRelease --quiet

APK="app/build/outputs/apk/release/app-release.apk"
OUT="RaspisanieSF-$VERSION.apk"
cp "$APK" "$OUT"

# --- бинарный патч от предыдущего релиза ---

PATCH_FILE=""
MANIFEST_FILE=""
PREV_TAG="$(git describe --tags --abbrev=0 2>/dev/null || true)"

if [[ -n "$PREV_TAG" ]]; then
    echo "==> Пробуем посчитать патч от $PREV_TAG"
    PREV_APK=".release-prev.apk"
    rm -f "$PREV_APK"

    if gh release download "$PREV_TAG" --pattern '*.apk' --output "$PREV_APK" 2>/dev/null; then
        # Java на Windows требует ';' в -cp, а git-bash умеет корректно
        # подставлять пути в этот вид только через cygpath -w: собранная
        # вручную POSIX-строка "a;b" молча теряет второй путь.
        find_jar() { find "$HOME/.gradle/caches/modules-2" -iname "$1" 2>/dev/null \
            | grep -v sources | grep -v javadoc | head -1; }
        JBSDIFF_JAR=$(find_jar 'jbsdiff-*.jar')
        COMPRESS_JAR=$(find "$HOME/.gradle/caches/modules-2" -path '*commons-compress/1.26.0*' -iname '*.jar' 2>/dev/null | grep -v sources | grep -v javadoc | head -1)
        COMMONS_IO_JAR=$(find_jar 'commons-io-*.jar')

        if [[ -n "$JBSDIFF_JAR" && -n "$COMPRESS_JAR" && -n "$COMMONS_IO_JAR" ]]; then
            TOOL_DIR=".release-tools"
            mkdir -p "$TOOL_DIR"
            CP="$(cygpath -w "$JBSDIFF_JAR");$(cygpath -w "$COMPRESS_JAR");$(cygpath -w "$COMMONS_IO_JAR")"
            TOOL_DIR_W="$(cygpath -w "$TOOL_DIR")"

            if javac -cp "$CP" -d "$TOOL_DIR" tools/ReleasePatch.java 2>/dev/null; then
                PATCH_FILE="RaspisanieSF-$VERSION-patch.bin"
                if java -cp "$TOOL_DIR_W;$CP" ReleasePatch diff "$PREV_APK" "$APK" "$PATCH_FILE" 2>/dev/null; then
                    BASE_SHA=$(sha256sum "$PREV_APK" | cut -d' ' -f1)
                    RESULT_SHA=$(sha256sum "$APK" | cut -d' ' -f1)
                    PATCH_SIZE=$(stat -c%s "$PATCH_FILE" 2>/dev/null || stat -f%z "$PATCH_FILE")
                    FULL_SIZE=$(stat -c%s "$APK" 2>/dev/null || stat -f%z "$APK")
                    PREV_VERSION="${PREV_TAG#v}"

                    MANIFEST_FILE="update-manifest.json"
                    PATCH_URL="https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/releases/download/v$VERSION/$PATCH_FILE"

                    cat > "$MANIFEST_FILE" <<JSON
{
  "version": "$VERSION",
  "apkSha256": "$RESULT_SHA",
  "patch": {
    "fromVersion": "$PREV_VERSION",
    "baseSha256": "$BASE_SHA",
    "url": "$PATCH_URL",
    "size": $PATCH_SIZE,
    "resultSha256": "$RESULT_SHA"
  }
}
JSON
                    PERCENT=$((PATCH_SIZE * 100 / FULL_SIZE))
                    echo "==> Патч готов: $PATCH_SIZE байт против $FULL_SIZE у полного APK (~$PERCENT%)"
                else
                    echo "==> Патч не посчитался — публикуем только полный APK" >&2
                    PATCH_FILE=""
                fi
            else
                echo "==> Не удалось собрать ReleasePatch.java — публикуем только полный APK" >&2
            fi
        else
            echo "==> jbsdiff/commons-compress/commons-io не найдены в кеше Gradle — публикуем только полный APK" >&2
        fi
    else
        echo "==> У релиза $PREV_TAG нет APK-ассета — публикуем только полный APK" >&2
    fi

    rm -f "$PREV_APK"
fi

echo "==> Коммит и тег"
git add "$GRADLE_FILE"
git commit -m "Версия $VERSION${NOTES:+: $NOTES}"
git tag "v$VERSION"
git push origin HEAD --tags

echo "==> Публикация релиза"
ASSETS=("$OUT")
[[ -n "$PATCH_FILE" ]] && ASSETS+=("$PATCH_FILE")
[[ -n "$MANIFEST_FILE" ]] && ASSETS+=("$MANIFEST_FILE")

gh release create "v$VERSION" "${ASSETS[@]}" \
    --title "Версия $VERSION" \
    --notes "${NOTES:-Обновление приложения}"

rm -f "$OUT" "$PATCH_FILE" "$MANIFEST_FILE"
rm -rf ".release-tools"
echo "==> Готово. Приложение предложит обновление при следующем запуске."
