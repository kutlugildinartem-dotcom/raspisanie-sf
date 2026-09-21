#!/usr/bin/env bash
# Выпуск новой версии: поднимает версию, собирает подписанный APK и публикует
# релиз на GitHub. Приложение увидит его при следующем запуске.
#
#   ./release.sh 1.1 "Что изменилось"
#
# versionCode обязан расти: Android не ставит поверх сборку с тем же или
# меньшим кодом, даже если versionName новее.
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

echo "==> Коммит и тег"
git add "$GRADLE_FILE"
git commit -m "Версия $VERSION${NOTES:+: $NOTES}"
git tag "v$VERSION"
git push origin HEAD --tags

echo "==> Публикация релиза"
gh release create "v$VERSION" "$OUT" \
    --title "Версия $VERSION" \
    --notes "${NOTES:-Обновление приложения}"

rm -f "$OUT"
echo "==> Готово. Приложение предложит обновление при следующем запуске."
