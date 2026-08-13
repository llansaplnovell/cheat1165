# primordial.jar — PlayerESP patch

Sources of the classes patched inside `primordial.jar` (a 1.8.9 client where the vanilla
classes are shipped deobfuscated, so they can be decompiled, edited and recompiled in place).
They are kept here because they do not exist anywhere else in source form.

## Changed classes

| Class | Why |
| --- | --- |
| `Magic/mod/s/render/PlayerESP.java` | Outline mode rewritten as a batched pass, glow added, `Other` mode removed |
| `net/minecraft/client/renderer/entity/RendererLivingEntity.java` | Silhouette render keeps the skin texture (alpha) bound; hook for the armour mask; the old per-entity outline passes are gone |

## What changed

1. **Both skin layers are outlined.** The silhouette used to be drawn with texturing off, so the
   second skin layer covered the whole model regardless of what the skin actually paints there and
   the outline sat one pixel off the body. It is now drawn with the skin bound and a texture
   environment that takes the colour from `glColor` and the alpha from the skin, so the alpha test
   drops the transparent parts of the second layer. The outline follows the first layer where the
   second one is empty and steps out over it where it is painted. Applies to Minecraft mode too.
2. **`Other` mode removed** from the mode list.
3. **Outline mode merges overlapping players.** Instead of clearing the stencil and outlining each
   entity on its own, every target is now drawn in one batch from `EventRender3D`: first the merged
   silhouette into the stencil, then a single wireframe pass clipped to the pixels the silhouette
   does not cover. Players standing next to each other therefore share one contour, like the
   Minecraft mode does.
4. **Glow.** Optional, with a `GlowLength` slider (pixels). The merged silhouette is rendered into a
   half resolution framebuffer, blurred by a separable gaussian (GLSL program compiled at runtime,
   as in `WorldParticles`) and blended back over the world with the same blend function. The stencil
   from the mask pass keeps it strictly outside the players — nothing is shaded under the models.

`ThroughArmor` still applies to Outline mode: with it off, armour joins the silhouette, so the
outline wraps the armour instead of cutting through it.

## Rebuilding

```sh
# decompile the two classes (CFR), edit, then:
javac -g --release 8 -cp primordial.jar -d out \
    src/Magic/mod/s/render/PlayerESP.java \
    src/net/minecraft/client/renderer/entity/RendererLivingEntity.java
cp primordial.jar primordial-new.jar
cd out && zip ../primordial-new.jar \
    'Magic/mod/s/render/PlayerESP.class' \
    'Magic/mod/s/render/PlayerESP$Mode.class' \
    'net/minecraft/client/renderer/entity/RendererLivingEntity.class' \
    'net/minecraft/client/renderer/entity/RendererLivingEntity$RendererLivingEntity$1.class'
```

The jar carries `SHA-256-Digest` entries in its manifest but no signature files, so replacing
entries needs no resigning.
