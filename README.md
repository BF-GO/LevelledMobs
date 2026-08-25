# Патчи русской сборки LevelledMobs

Эта orphan-ветка содержит только воспроизводимую серию патчей для русской сборки [BF-GO/LevelledMobs](https://github.com/BF-GO/LevelledMobs). Исходный проект: [ArcanePlugins/LevelledMobs](https://github.com/ArcanePlugins/LevelledMobs).

Проверенная база текущей серии указана в `BASE_COMMIT`:

```text
ab061bd46675941e4326a540ee58992b27113696
```

Порядок применения хранится в `series`. Каждый патч создан командой `git format-patch --full-index --binary`.

## Ветки форка

- `master` — чистое зеркало `upstream/master`, без русификации.
- `production` — основная русская сборка, воспроизводимая этой серией.
- `patches` — только патчи и инструкция по их сопровождению.

## Применение текущей серии

Добавьте upstream и получите его состояние, затем создайте рабочую ветку от проверенной базы:

```powershell
git remote add upstream https://github.com/ArcanePlugins/LevelledMobs.git
git fetch upstream
git switch -c production-rebuild ab061bd46675941e4326a540ee58992b27113696
```

В PowerShell примените manifest по порядку:

```powershell
Get-Content series | ForEach-Object {
    git am -3 $_
    if ($LASTEXITCODE -ne 0) { break }
}
```

После применения дерево должно совпадать с `production`:

```powershell
git diff --exit-code production production-rebuild
```

## Обновление на свежий upstream

1. Получите изменения: `git fetch upstream`.
2. Создайте новую временную ветку от свежего `upstream/master`.
3. Примените патч-сеты строго по файлу `series` командой `git am -3`.
4. При конфликте исправьте файлы, выполните `git add <файлы>` и `git am --continue`. Для отмены используйте `git am --abort`.
5. Проверьте UTF-8, YAML, ключи каталога, плейсхолдеры, цветовые коды и полную сборку.
6. Перегенерируйте через `git format-patch --full-index --binary` только те серии, которые пришлось изменить из-за нового upstream.
7. Пересоздайте `production` от свежего `upstream/master` повторным применением итогового manifest и только после проверки опубликуйте её.

Не редактируйте старые патч-сеты ради независимых будущих доработок. Добавляйте такие изменения отдельными следующими каталогами `patchsets/0005-*`, `patchsets/0006-*` и включайте их в конец `series`. Это сохраняет понятную границу между русификацией и последующими изменениями форка.

## Проверка сборки

Upstream-зависимость `WorldGuard 7.1.0-SNAPSHOT` сейчас опубликована для JVM 25, тогда как проект нацелен на JVM 21. Для локальной проверки допустима временная подмена зависимости на совместимую `WorldGuard 7.0.16`; такую подмену нельзя включать в патчи.
