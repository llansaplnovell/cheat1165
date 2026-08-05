# PlayerESP — перенос в Magic (primordial), MC 1.8.x

Дословный перенос PlayerESP из Peter-клиента на API Magic. Все 5 режимов
`ESPModes` работают. Файлы:

- `src/main/java/Magic/mod/s/render/PlayerESP.java` — сам модуль
- `src/main/java/Magic/mod/s/render/tools/PatchMagic.java` — патчер двух
  ванильных классов рендера (standalone-утилита, не часть сборки мода)

Модуль регистрировать вручную не нужно — `Modules.loadModules()` сканирует
`Magic.mod.s.*` по classpath и сам инстанциирует подкласс `Module`.

---

## FIX: чёрный экран (настоящая причина)

Первая версия патча `RenderGlobal.isRenderEntityOutlines()` возвращала
`true` безусловно — ровно как у Peter. У Peter так можно, **у него нет
OptiFine**. В Magic OptiFine специально закрывает этот проход условием
`!(Config.isFastRender() || isShaders() || isAntialiasing())`, потому что
он с ними несовместим: код ниже делает
`mc.getFramebuffer().bindFramebuffer(false)` посреди пайплайна OptiFine —
экран чёрный.

Режим `Minecraft` — дефолтный (ordinal 0 у `Mode`), а `CGui.saveNew()`
сохраняет включённое состояние модуля при выходе. Поэтому, один раз
включив PlayerESP, чёрный экран получался при **каждом** следующем заходе
в мир. Промежуточный "фикс" с null-check не помогал — framebuffer и
shader были не-null, дело было именно в обходе гварда OptiFine.

Исправлено: гвард OptiFine учитывается и в нашей ветке. Где эффект вообще
может работать — картинка идентична Peter; где OptiFine его запрещает —
режим просто ничего не рисует вместо чёрного экрана.

---

## Где живут Minecraft и Outline

Оба режима у Peter реализованы **не в модуле**, а патчами ванильных
классов рендера. Установлено декодированием обфусцированных строк тем же
алгоритмом, что в самом клиенте (`(c + n2 - key[i % len]) & 0xFFFF`) —
строки раскодировались в `"ESPModes"` / `"Outline"` / `"Minecraft"`.

### Outline → `fZ.java` (= `RendererLivingEntity`)

Внутри `doRender`, в ветке без `renderOutlines`, сразу после
`setDoRenderBrightness`, идёт 4-проходный stencil-силуэт:

```
renderModel();  stencilSetup()                 <- Ob0183.ModFullBright()
renderModel();  stencilFillPass()              <- Ob0183.Ob0033()
renderModel();  stencilOutlinePass()           <- Ob0183.Ob0272()
applyOutlineColor(entity); outlineDrawState()  <- Ob0183.Ob0171()
renderModel();  stencilTeardown()              <- Ob0183.Ob0123()
renderModel();  // обычный вызов продолжается
```

Все GL-константы перенесены буквально: `glStencilFunc(512,1,15)` /
`(512,0,15)` / `(514,1,15)`, `glStencilOp(7681,...)` / `(7680,...)`,
`glPolygonMode(1032, 6913/6914)`, `glPolygonOffset(1, ∓2000000)`,
`glPushAttrib(1048575)`, `glLineWidth(2)`, lightmap 240/240.

Важная деталь, упущенная в промежуточной версии: у MC во фреймбуфере
**нет stencil-буфера**, его надо доцепить (`DEPTH24_STENCIL8`). У Peter
это `Ob0183.Ob0254()`; в Magic для этого уже есть готовый
`Magic.utils.render.StencilUtil.checkSetupFBO()` — используется он.

Как у Peter, Outline применяется ко **всем** `EntityLivingBase` кроме
своего игрока (`entity instanceof nH && entity != f.jF`), а не только к
игрокам.

Патч сделан не вставкой в середину метода, а переписыванием самого
call-site `renderModel()` (javassist `ExprEditor`): порядок инструкций и
все пять отрисовок те же, а гвард `!renderOutlines` воспроизводит
расположение Peter'а внутри else-ветки (в ветке vanilla-outline
`renderModel` тоже вызывается и трогать её нельзя).

### Minecraft → `ed.java` (= `RenderGlobal`)

Peter добавляет свою проверку в `isRenderEntityOutlines()`, переиспользуя
нативный ванильный spectator-glow проход
(`entityOutlineFramebuffer` / `entityOutlineShader`,
`shaders/post/entity_outline.json`) вместо собственной отрисовки.

Здесь то же самое, плюс два дополнительных условия (см. FIX выше):
гвард OptiFine и null-check на framebuffer/shader — всё, что ниже по
коду, разыменовывает эти поля без проверок.

---

## Режимы, реализованные в самом модуле

- **Corner** — двойная угловая скобка (32 прямоугольника: 8 цветных + 24
  чёрной обводки), billboard, координаты 1:1 из
  `Ob0183.ModSpeed(x,y,z,color)`. Позиция интерполированная (partialTicks).
- **Box** — проволочный AABB, **без интерполяции** (сырые `posX/posY/posZ`,
  как в `Ob0110.Ob0186()`, где читались `entity.aqZ/ara/arb` напрямую —
  поэтому слегка дёргается между тиками, это оригинальное поведение).
  Бокс уже реального хитбокса (0.5 вместо 0.6), depth-test всегда
  выключен — сквозь стены, тумблера не было.
- **Other** — рамка (4 стороны) белым + один цветной акцентный
  прямоугольник, billboard, интерполированная позиция.

## Цвета — сохранены оригинальные особенности, а не «исправлены»

- **Corner**: `hurtTime>0` → оранжево-красный (255,50,10), иначе друг →
  белый, иначе акцентный цвет клиента.
- **Box**: в оригинале и friend-, и non-friend-ветка резолвились в один и
  тот же литерал (белый) — воспроизведено как есть.
- **Other**: `FriendManager` не проверяется вообще (только hurt-flash vs
  акцентный цвет) — в оригинале тоже так.
- **Outline**: свой красный (255,**10**,10), отличный от Corner/Other
  (255,**50**,10) — тоже как в оригинале.

## Настройки

Одна, как у Peter: комбо `Mode` (`Minecraft` / `Outline` / `Corner` /
`Box` / `Other`, порядок оригинальный). Акцентный цвет берётся из клиента
(`Magic.getClientColor()` — аналог `Ob0106.Ob0219()`), отдельной
настройки цвета у оригинала не было.

---

## Как пересобрать

```bash
# 1) распаковать jar
unzip -q primordial.jar -d classes && rm -f classes/module-info.class

# 2) собрать модуль
javac -cp classes -d classes PlayerESP.java

# 3) применить оба патча (javassist уже лежит внутри primordial.jar)
javac -cp classes -d . tools/PatchMagic.java
java  -cp .:classes PatchMagic classes out

# 4) вложить обратно
jar uf primordial.jar -C classes Magic/mod/s/render/PlayerESP.class
jar uf primordial.jar -C classes 'Magic/mod/s/render/PlayerESP$Mode.class'
jar uf primordial.jar -C out net/minecraft/client/renderer/RenderGlobal.class
jar uf primordial.jar -C out net/minecraft/client/renderer/entity/RendererLivingEntity.class
```

## Что проверено, а что нет

Проверено: обе вставки декомпилируются ровно в ожидаемый код; все три
класса грузятся и линкуются под `-Xverify:all` (не VerifyError);
структура zip и манифест (`Main-Class: Start`) целы.

Не проверено: как оно выглядит в живой игре — графического клиента в
среде сборки нет. Визуальную корректность силуэта и glow подтвердить
могу только логикой соответствия оригиналу, не глазами.
