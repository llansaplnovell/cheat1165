# Color Picker + alpha, копирование/вставка цвета, цвет для PlayerESP

Готовые файлы для клиента **primordial** (`Magic.*`, Minecraft 1.8.9) из
`5b87eb4e-primordial.jar`. Всё в `src/` лежит по тем же путям, что и в клиенте —
кладёшь поверх своих исходников.

Все 8 файлов собраны `javac --release 8` с classpath’ом самого клиентского jar’а,
ошибок нет.

---

## Что появилось

### 1. Два элемента вместо одного

| Класс значения | Класс пикера | Что рисует | Куда ставить |
|---|---|---|---|
| `ColorValue` (был) | `ColorPicker` | квадрат HSB + полоса оттенка | там, где прозрачность не нужна — ClickGui и т.п. |
| **`ColorAlphaValue`** (новый) | **`ColorPickerAlpha`** (новый) | то же самое **+ полоса alpha** рядом | там, где полупрозрачность нужна — ESP, худ, трейсеры |

Названия классов сделаны так, чтобы в следующих запросах не гадать: нужен
ползунок прозрачности — `ColorAlphaValue`, не нужен — `ColorValue`. Объявляются
одинаково:

```java
// без alpha
public ColorValue color = new ColorValue("Color", this, Color.cyan, "Click Gui color.");

// с alpha
private final ColorAlphaValue color =
        new ColorAlphaValue("Color", this, new Color(255, 255, 255, 255), "ESP color and transparency.");
```

`ColorAlphaValue extends ColorValue`, поэтому сохранение конфига (`CGui.saveNew`
/ `loadNew`) подхватывает его само — там уже пишется `r,g,b,a`. **`CGui.java`
править не надо.**

Полоса alpha не подписана (как и просил): просто вторая полоска справа от
полосы оттенка, сверху непрозрачно — снизу прозрачно, под градиентом шахматка,
чтобы нижний край было видно. Текущее значение отмечено такими же чёрными
рисками, как на полосе оттенка.

### 2. Кнопки «C» и «P»

Под прямоугольником с кодом цвета — две мини-кнопки 9×9: **C** — copy,
**P** — paste. Есть у обоих пикеров.

* **C** — кладёт в системный буфер `#RRGGBB` (у `ColorPickerAlpha` —
  `#RRGGBBAA`).
* **P** — читает из буфера и применяет. Понимает `#RRGGBB`, `RRGGBB`,
  `0xRRGGBB` и те же три с хвостом `AA`.
  * в пикере без alpha альфа из буфера игнорируется, у значения остаётся своя;
  * в пикере с alpha 6-значный код меняет только RGB, альфу оставляет.

Клик по кнопкам идёт через `ValuePanel.mouseClicked` → `picker().mouseClicked`,
то есть срабатывает один раз на нажатие, а не каждый кадр.

### 3. Цвет во всех режимах PlayerESP

У модуля появилось значение `Color` (`ColorAlphaValue`, по умолчанию белый,
альфа 255). Его читают все пять режимов:

| Режим | Что красится |
|---|---|
| `Corner` | уголки-скобки |
| `Box` | рамка бокса (альфа тоже применяется, раньше было жёстко `1.0f`) |
| `Other` | боковая полоса; рамка остаётся белой, но берёт альфу настройки |
| `Outline` | `applyOutlineColor` — обводка по стенсилу |
| `Minecraft` | ванильная обводка — **нужен патч**, см. `patches/` |

Подсветка урона (красный) и френдов (белый) осталась, но обе теперь наследуют
альфу из настройки.

---

## Файлы

```
src/Magic/utils/render/ColorPicker.java            изменён
src/Magic/utils/render/ColorPickerAlpha.java       новый
src/Magic/mod/value/Type.java                      изменён  (+ COLOR_ALPHA)
src/Magic/mod/value/values/ColorValue.java         изменён
src/Magic/mod/value/values/ColorAlphaValue.java    новый
src/Magic/clickgui/panels/ValuePanel.java          изменён
src/Magic/clickgui/panels/ModulePanel.java         изменён
src/Magic/mod/s/render/PlayerESP.java              изменён
patches/RendererLivingEntity.setScoreTeamColor.md  патч ванилы (только Minecraft-режим)
```

Если не хочешь заменять файлы целиком — вот что в них реально поменялось:

* **`ColorPicker`** — расположение блока с кодом цвета вынесено в
  `infoOffsetX()` / `infoWidth()`, подпись — в `hexLabel()`, добавлены
  `drawExtraStrips()` (хук для полосы alpha), `setApplier()`, `mouseClicked()`,
  `copy()`, `paste()`, `parseColor()`. Заодно `Integer.toHexString(rgb).substring(2)`
  заменён на `String.format("%06X", ...)` — старый вариант ломался на цветах с
  нулём в старшем байте.
* **`ColorValue`** — поля `color` / `picker` стали `protected`, появились
  `createPicker()` и `onPicked()` (обе переопределяются в `ColorAlphaValue`),
  в конструкторе вешается `picker.setApplier(this::setValue)` для кнопки paste.
  При выборе цвета мышью альфа значения больше не сбрасывается в 255.
* **`Type`** — добавлена константа `COLOR_ALPHA`.
* **`ValuePanel`** — новый `case COLOR_ALPHA` в `render`, обработка `COLOR` и
  `COLOR_ALPHA` в `mouseClicked` и `isHovered`, высота строки в конструкторе.
* **`ModulePanel`** — в `rePositionValues()` высота 86 теперь и для
  `COLOR_ALPHA`.

## Размеры

Пикеру с alpha полоса и более длинная подпись съедают 11 px, поэтому
`ValuePanel` даёт ему градиент шириной **46** вместо **68** — правый край у
обоих вариантов совпадает, вёрстка панели не разъезжается. Константы лежат в
`ValuePanel.COLOR_PICKER_WIDTH` / `COLOR_ALPHA_PICKER_WIDTH`, высота строки —
в `ModulePanel.COLOR_ROW_HEIGHT`.

```
без alpha:   [ градиент 68 ][оттенок]      [#RRGGBB  ]
                                           [C][P]
с alpha:     [ градиент 46 ][оттенок][альфа][#RRGGBBAA]
                                           [C][P]
```
