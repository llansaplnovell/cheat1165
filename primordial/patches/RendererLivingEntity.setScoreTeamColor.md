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

## Как было

```java
protected boolean setScoreTeamColor(EntityLivingBase entityLivingBaseIn) {
    int i = 16777215;

    if (entityLivingBaseIn instanceof EntityPlayer) {
        ScorePlayerTeam scoreplayerteam = (ScorePlayerTeam) entityLivingBaseIn.getTeam();

        if (scoreplayerteam != null) {
            String s = FontRenderer.getFormatFromString(scoreplayerteam.getColorPrefix());

            if (s.length() >= 2) {
                i = this.getFontRendererFromRenderManager().getColorCode(s.charAt(1));
            }
        }
    }

    float f1 = (float) (i >> 16 & 255) / 255.0F;
    float f2 = (float) (i >> 8 & 255) / 255.0F;
    float f = (float) (i & 255) / 255.0F;
    GlStateManager.disableLighting();
    GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    GlStateManager.color(f1, f2, f, 1.0F);
    ...
}
```

## Как надо

Добавляются переменная `alpha`, блок с хуком и `alpha` вместо `1.0F` в
`GlStateManager.color`. Больше в методе ничего не меняется.

```java
protected boolean setScoreTeamColor(EntityLivingBase entityLivingBaseIn) {
    int i = 16777215;
    float alpha = 1.0F;                                          // <-- 1. добавить

    if (entityLivingBaseIn instanceof EntityPlayer) {
        ScorePlayerTeam scoreplayerteam = (ScorePlayerTeam) entityLivingBaseIn.getTeam();

        if (scoreplayerteam != null) {
            String s = FontRenderer.getFormatFromString(scoreplayerteam.getColorPrefix());

            if (s.length() >= 2) {
                i = this.getFontRendererFromRenderManager().getColorCode(s.charAt(1));
            }
        }
    }

    int espOverride = PlayerESP.outlineColorOverride(entityLivingBaseIn);   // <-- 2. добавить
    if (espOverride != 0) {                                                 //     блок
        i = espOverride & 16777215;
        alpha = (float) (espOverride >> 24 & 255) / 255.0F;
    }

    float f1 = (float) (i >> 16 & 255) / 255.0F;
    float f2 = (float) (i >> 8 & 255) / 255.0F;
    float f = (float) (i & 255) / 255.0F;
    GlStateManager.disableLighting();
    GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    GlStateManager.color(f1, f2, f, alpha);                      // <-- 3. было 1.0F
    GlStateManager.disableTexture2D();
    GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
    GlStateManager.disableTexture2D();
    GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    return true;
}
```

Импорт `Magic.mod.s.render.PlayerESP` в этом файле уже есть (там же лежат вызовы
`stencilOutlineBeforeLayers` / `applyOutlineColor`), добавлять не нужно.

## Поведение

* `outlineColorOverride` возвращает `0`, когда PlayerESP выключен или стоит не в
  режиме `Minecraft`, — тогда всё работает ровно как раньше, по цвету команды.
* Возвращаемое значение — ARGB настройки `Color`. Альфа попадает в
  outline-фреймбуфер и композитится шейдером, так что ползунок прозрачности
  работает и здесь.
* Единственный «слепой» случай: полностью прозрачный чёрный (`#00000000`) даёт
  `0` и читается как «оверрайда нет». Такой цвет всё равно ничего не рисует.
