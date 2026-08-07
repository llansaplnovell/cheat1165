# Патч: цвет ESP в режиме `Minecraft`

Нужен **только** для режима `PlayerESP.Mode.Minecraft` (ванильная обводка через
шейдер `shaders/post/magic_esp_outline.json`). Остальные четыре режима
(`Outline`, `Corner`, `Box`, `Other`) берут цвет и альфу из настройки сами,
никаких правок ванилы не требуют.

Причина: в этом режиме сущности рисуются в outline-фреймбуфер обычным ванильным
кодом, а цвет там задаёт `RendererLivingEntity.setScoreTeamColor` — цветом
команды из скорборда (по умолчанию белый). Клиент в этот метод пока не влезает,
поэтому нужен один хук.

## Файл

`net/minecraft/client/renderer/entity/RendererLivingEntity.java`

## Изменение — одна строка

В самом конце `setScoreTeamColor` было:

```java
    float f1 = (float) (i >> 16 & 255) / 255.0F;
    float f2 = (float) (i >> 8 & 255) / 255.0F;
    float f = (float) (i & 255) / 255.0F;
    GlStateManager.disableLighting();
    GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    GlStateManager.color(f1, f2, f, 1.0F);                                 // <--
    GlStateManager.disableTexture2D();
```

Стало:

```java
    float f1 = (float) (i >> 16 & 255) / 255.0F;
    float f2 = (float) (i >> 8 & 255) / 255.0F;
    float f = (float) (i & 255) / 255.0F;
    GlStateManager.disableLighting();
    GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    PlayerESP.outlineTeamColor(f1, f2, f, 1.0F, entityLivingBaseIn);       // <--
    GlStateManager.disableTexture2D();
```

Импорт `Magic.mod.s.render.PlayerESP` в этом файле уже есть (рядом лежат вызовы
`stencilOutlineBeforeLayers` / `applyOutlineColor`), добавлять не нужно.

## Поведение

`PlayerESP.outlineTeamColor` спрашивает `outlineColorOverride`:

* модуль выключен или стоит не в режиме `Minecraft` → возвращается `0`, и метод
  просто зовёт `GlStateManager.color` с переданным цветом команды, то есть всё
  работает ровно как раньше;
* иначе берётся ARGB настройки `Color`. Альфа попадает в outline-фреймбуфер и
  композитится шейдером, так что ползунок прозрачности работает и здесь;
* единственный «слепой» случай: полностью прозрачный чёрный (`#00000000`) даёт
  `0` и читается как «оверрайда нет». Такой цвет всё равно ничего не рисует.

## Если правишь не исходники, а готовый jar

`tools/patch-jar.sh` делает ровно это же на байткоде: находит в
`setScoreTeamColor` единственный вызов `GlStateManager.color(FFFF)V` и заменяет
его на `ALOAD 1` + `PlayerESP.outlineTeamColor(FFFFLEntityLivingBase;)V`.
Остальной класс не трогается.
