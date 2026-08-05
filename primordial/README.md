# TpAura port (Mesir -> Primordial/Magic)

Порт модуля `TpAura` из `MesirClient1.8.8.jar` (`ipana.modules.combat.TpAura`)
в клиент Primordial (`Magic`, 1.8.9).

## Файлы

| Файл | Источник в Mesir |
| --- | --- |
| `src/Magic/mod/s/combat/TpAura.java` | `ipana/modules/combat/TpAura` |
| `src/Magic/utils/pathfind/astar/AStar.java` | `ipana/utils/pathfind/astar/AStar` |
| `src/Magic/utils/pathfind/astar/Node.java` | `ipana/utils/pathfind/astar/Node` |

## Маппинг зависимостей

| Mesir | Primordial |
| --- | --- |
| `ipana.managements.module.Module` / `Category` | `Magic.mod.Module` / `Magic.mod.Category` |
| `ipana.managements.value.values.BoolValue` / `EnumValue` | `Magic.mod.value.values.BoolValue` / `EnumValue` |
| `ipana.events.EventPreUpdate` / `EventPostUpdate` / `EventRender3D` | `Magic.ink.event.s.EventPreUpdate` / `EventPostUpdate` / `EventRender3D` |
| `ipana.utils.player.PlayerUtils` (`packet`, `send`, `sendOffset`, `calculate2`, `getBaseMoveSpeed`) | `Magic.utils.player.ClientUtils` (те же сигнатуры и тела) |
| `ipana.utils.player.RotationUtils` (`getRotations`, `getRotationFromPosition`) | `Magic.utils.player.RotationUtils` (идентичные реализации) |
| `ipana.utils.Timer` | `Magic.utils.math.Timer` |
| `ipana.managements.friend.FriendManager` | `Magic.utils.Friend.FriendManager` |
| `Ipana.getClientColor()` + локальный `drawOutlineBox` | `Magic.utils.render.RenderUtils.drawOutlineBox` (уже красит в клиентский цвет) |
| `BlockPos.distanceTo(BlockPos)` (патч ванилы в Mesir) | приватный `AStar.distance(BlockPos, BlockPos)` — в Primordial ваниль не патчена |

Обе сборки используют один и тот же `pisi.unitedmeows.eventapi`, поэтому
`Listener<T>` + `filter(...)` перенесены без изменений.

## Регистрация

Ручная регистрация не нужна: `Magic.mod.Modules` сканирует пакет `Magic/mod/s/`
и создаёт все top-level классы-наследники `Module` через пустой конструктор.
Вложенный `TpMode` сканером пропускается. ClickGui и конфиг (`CGui.saveNew` /
`loadNew`) работают по `ModuleManager` / `ValueManager`, поэтому модуль и его
значения (`Mode`, `Block`) сохраняются автоматически.

Бинд по умолчанию — `35` (H), как в Mesir; в Primordial этот код не занят.

## Сборка

```
javac --release 8 -proc:none -cp primordial.jar -d out \
    src/Magic/mod/s/combat/TpAura.java \
    src/Magic/utils/pathfind/astar/AStar.java \
    src/Magic/utils/pathfind/astar/Node.java
jar uf primordial.jar -C out Magic
```

Компилируется без ошибок и предупреждений против оригинального `primordial.jar`
(bytecode 52 / Java 8, как у остального клиента).
