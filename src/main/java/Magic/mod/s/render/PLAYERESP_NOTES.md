# PlayerESP — перенос в Magic (primordial), MC 1.8.9

## Что это
Оригинальная реализация PlayerESP под архитектуру `Magic` (компилированный
клиент из `primordial.jar`), написанная с нуля под её собственные API —
не построчный перенос из декомпила другого клиента (там была другая, старая
рендер-система на `GL11.glBegin`/кастомном `Ob0183`, несовместимая с этой
кодовой базой напрямую).

Файл: `src/main/java/Magic/mod/s/render/PlayerESP.java`

## Куда класть
В реальный проект `Magic`, по тому же package path:
`src/main/java/Magic/mod/s/render/PlayerESP.java`

Регистрировать вручную не нужно — `Modules.loadModules()` сканирует
`Magic.mod.s.*` по classpath и сам инстанциирует любой конкретный
подкласс `Module`, который там найдёт.

## Зависимости — всё уже есть в Magic, ничего докидывать не нужно
- `Magic.mod.Module`, `Category.Render`
- `Magic.mod.value.values.EnumValue/BoolValue/ColorValue`
- `Magic.ink.event.s.EventRender3D` + `pisi.unitedmeows.eventapi.event.listener.Listener`
  (подписка на рендер мира происходит автоматически через
  `EventManager.eventSystem.subscribeAll(this)` в `Module.onEnable()`)
- `Magic.utils.Friend.FriendManager.isFriend(String)`
- `Magic.utils.player.ClientUtils.getPlayers()`

## Настройки модуля
- **Mode**: `Box` (проволочный контур) / `Fill` (полупрозрачная заливка)
- **ThroughWalls**: рисовать сквозь блоки (отключает depth-test) или только when in line of sight
- **HealthColor**: подмешивать белый цвет к базовому по мере восстановления HP
- **Friends**: красить друзей из `FriendManager` отдельным цветом (голубой)
- **Color**: базовый цвет для не-друзей (по умолчанию красный)

## Отличия от исходной логики (Peter/декомпил)
- Вместо ручной интерполяции camera-offset (`fy.Vk/Vl/Vm`) используется
  `mc.getRenderManager().renderPosX/Y/Z` — тот же смысл, актуальное для
  этой кодовой базы имя (см. `NameTags.renderNametag` в том же клиенте).
- Вместо кастомного билборд-рендера ("Corner"/"Other" с ручными GL-вершинами
  иконки) — два режима, оба через `AxisAlignedBB`: контур и заливка.
  Так проще поддерживать и это сочетается с уже существующим
  `RenderUtils.drawOutlineBox` в стиле того же клиента.
