# PlayerESP — перенос в Magic (primordial), MC 1.8.9

## Что это
Дословный перенос PlayerESP из декомпила Peter-клиента (`Ob0110.java` +
рендер-утилита `Ob0183.java`) на API Magic. Все режимы, все магические
числа и все особенности выбора цвета — скопированы как есть, а не
переизобретены. Список конкретных соответствий — в шапке самого файла
`PlayerESP.java`.

Файл: `src/main/java/Magic/mod/s/render/PlayerESP.java`

## Куда класть
В реальный проект `Magic`, по тому же package path:
`src/main/java/Magic/mod/s/render/PlayerESP.java`

Регистрировать вручную не нужно — `Modules.loadModules()` сканирует
`Magic.mod.s.*` по classpath и сам инстанциирует любой конкретный
подкласс `Module`, который там найдёт.

## UPDATE: Minecraft/Outline на самом деле реализованы (не dead code)
Я ошибся в первой версии этого файла — проверил только `Ob0110.java` и
решил, что "Minecraft"/"Outline" ничего не делают. Дозагрузил полный
деобф-джар и раскодировал строки тем же XOR-подобным алгоритмом, что в
самом клиенте (`(c + n2 - key[i % len]) & 0xFFFF`), — оказалось, оба
режима реализованы патчами в ванильных классах рендера, а не в самом
`Ob0110`:

- **"Outline"** пропатчен в `fZ.java` (= `RendererLivingEntity` по MCP-именам
  Magic) — проверка `PlayerESP.isEnabled() && ESPModes=="Outline"` включает
  multi-pass рендер через `GL_STENCIL_TEST`: настоящая 3D-модель игрока
  рисуется несколько раз, создавая цветной силуэт-контур (не плоскую
  иконку).
- **"Minecraft"** пропатчен в `ed.java` (= `RenderGlobal`) — условие
  (строки декодируются в `"ESPModes"`/`"Minecraft"`) добавляет `||` в
  ванильный `isRenderEntityOutlines()` (используется для spectator-glow),
  то есть режим просто переиспользует нативный ванильный
  `entityOutlineFramebuffer`/`entityOutlineShader`
  (`shaders/post/entity_outline.json`) вместо своей отрисовки.

**Хорошая новость**: `net.minecraft.client.renderer.RenderGlobal` и
`RendererLivingEntity` в самом Magic (`primordial.jar`) — 100% чистый
ванильный+Optifine код, ничего не сломано и не пропатчено под это. Вся
инфраструктура (framebuffer, шейдер, фильтр по `EntityPlayer`, team-color
тонирование через флаг `renderOutlines`) на месте и рабочая.

### Как перенёс
- **"Outline"** — сделал **без патча jar вообще**, чисто в модуле
  (`renderOutline()`): публичный `RenderManager.renderEntityStatic(...)` +
  стандартный 2-проходный stencil-silhouette (проход 1 — модель в stencil
  без цвета через `glColorMask(false,...)`; проход 2 — увеличенная копия с
  инвертированным stencil-тестом, красится в наш цвет, без depth-test —
  сквозь стены). Это не байт-в-байт копия `Ob0183`'s GL-вызовов (там не до
  конца понятен маппинг конкретных `glStencilFunc`/`glStencilOp` констант),
  а стандартная, проверенная реализация того же эффекта.
- **"Minecraft"** — единственное место, где реально пришлось патчить
  скомпилированный класс: `RenderGlobal.isRenderEntityOutlines()` теперь
  сначала проверяет `PlayerESP.wantsVanillaOutline()` и возвращает `true`,
  если да — иначе ведёт себя как раньше (ванильное spectator-условие).
  Патч сделан через **Javassist** (`CtMethod.insertBefore(...)`) — он был
  прямо в `primordial.jar` как библиотека, так что не пришлось ничего
  докачивать. Задет только один метод, всё остальное в классе — as-is.

### Ограничение, которое стоит знать
Я не могу запустить сам Minecraft в этой среде, так что зрительно
подтвердить, что "Outline"-силуэт и "Minecraft"-glow выглядят именно так,
как надо, я не могу — только то, что байткод компилируется, резолвится и
логически соответствует описанному механизму. `isRenderEntityOutlines()`
после патча декомпилируется ровно так, как задумано (см. коммит).

## Режимы (те же 5 пунктов, что в оригинальном ESPModes)
- **Corner** — двойная угловая скобка (32 прямоугольника: 8 цветных +
  24 чёрной обводки), billboard, координаты 1:1 из `Ob0183.ModSpeed(x,y,z,color)`.
  Позиция интерполированная (partialTicks), как в оригинале.
- **Box** — проволочный AABB через `drawSelectionBoundingBox`-алгоритм,
  **без интерполяции** (сырые `posX/posY/posZ`, как в `Ob0110.Ob0186()` —
  entity.aqZ/ara/arb там читались напрямую). Бокс уже реального хитбокса
  (0.5 вместо 0.6), depth test всегда выключен — сквозь стены без тумблера,
  как в источнике.
- **Other** — простая рамка (4 стороны) белым + один цветной акцентный
  прямоугольник, тоже billboard, интерполированная позиция.

## Цвета — сохранены оригинальные особенности, а не "исправлены"
- **Corner**: hurt-flash (`hurtTime>0`) → оранжево-красный, иначе друг →
  белый, иначе `baseColor`.
- **Box**: в оригинале и friend-, и non-friend-ветка резолвились в один и
  тот же литерал (белый) — это дословно воспроизведено, не заменено на
  `baseColor` для non-friend случая.
- **Other**: вообще не проверяет `FriendManager` (только hurt-flash vs
  `baseColor`) — в оригинале тоже так.

## Единственное место, где пришлось принять решение (не 1:1 проверено)
Внутри billboard-блока `Ob0183` было два вызова через обфусцированные
имена (`dT.hG()`, `dT.Ob0091(true)`) сразу после `glDisable(GL_DEPTH_TEST)`.
Их смысл почти наверняка `disableTexture2D()`/`depthMask(true)` (судя по
такому же паттерну в других местах того же класса), но 100% не проверено
рендером — я не могу запустить графический клиент в этой сессии, чтобы
сверить визуально. На итоговую картинку это не влияет: рисуются сплошные
непрозрачные прямоугольники без текстуры, `depthMask` при выключенном
depth test почти ни на что не влияет.

## Зависимости — всё уже есть в Magic
`Module`, `Category.Render`, `EnumValue`, `ColorValue`,
`Magic.ink.event.s.EventRender3D` + `pisi.unitedmeows.eventapi` `Listener`,
`Magic.utils.Friend.FriendManager.isFriend`, `Magic.utils.player.ClientUtils.getPlayers`,
плюс ванильные `Gui.drawRect` / `Tessellator` / `WorldRenderer` / `GlStateManager`.
Ничего докидывать не пришлось.
