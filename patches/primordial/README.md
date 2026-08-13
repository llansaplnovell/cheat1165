# primordial.jar — PlayerESP / NameTags patch

Sources of the classes patched inside `primordial.jar` (a 1.8.9 client where the vanilla
classes are shipped deobfuscated, so they can be decompiled, edited and recompiled in place).
They are kept here because they do not exist anywhere else in source form.

## Changed classes

| Class | Why |
| --- | --- |
| `Magic/mod/s/render/PlayerESP.java` | Outline mode rebuilt as a screen space pass over the silhouette, glow, `Other` mode removed |
| `Magic/mod/s/render/NameTags.java` | Absorbed the FishHookNametag module as a setting |
| `net/minecraft/client/renderer/entity/RendererLivingEntity.java` | Silhouette keeps the skin alpha and can include the armor layers |
| `Magic/mod/s/render/FHNametag.class` | **deleted from the jar** — modules are found by scanning the package, so removing the class unregisters it |

## How the Outline mode works now

The old implementation drew a thick wireframe of the model and cut away the half that fell inside
the model. A wireframe follows polygon edges, but the visible shape follows the alpha test, so the
two disagreed wherever a skin's second layer was partly transparent: doubled lines where both
layers had an edge, and missing segments where the shape stepped between the layers.

The line is now derived from the shape itself:

1. every target is drawn once into an offscreen silhouette, colored per player, with the alpha
   taken from the skin (see below);
2. optionally the silhouette is blurred at half resolution with a separable gaussian — that is
   the glow;
3. one full screen pass turns it into the result: pixels the silhouette covers are discarded, the
   ring of pixels next to it becomes the line, and the blurred copy fills the rest with the glow.

Because everything comes from one merged silhouette, players who overlap on screen share a single
contour, the second skin layer can never produce a line of its own, and the glow only ever exists
outside the players — nothing is shaded underneath the models.

## The silhouette shader

The silhouette used to be drawn with texturing off, so the second skin layer covered the whole
model regardless of what the skin paints there and the outline sat one pixel off the body. Feeding
the skin back in through the fixed function pipeline fixed the shape but left the colour and the
alpha at the mercy of whatever the render path did next, which produced three visible faults: the
armor came out white (the enchantment glint blends additively on top of it), and near transparent
pixels of the second layer wrote a lower alpha over the opaque body, so details like a skin's
glasses picked up a contour of their own inside the model.

Both modes now draw the silhouette through a small shader instead: the skin decides only what is
kept (discarded below the same 0.1 the game itself uses) and every surviving fragment writes the
same colour and the same alpha. On top of that blending is held off for the whole silhouette — the
state manager is left believing it is on, so the glint's `enableBlend()` never reaches the driver.
Nothing inside the shape can differ from anything else inside the shape, so the only edge that can
exist is the border of the whole shape: the line runs along the first layer and steps out over the
second one wherever it is actually painted.

## Settings

- **ThroughArmor** (Minecraft + Outline): when on, the armor layers join the silhouette, so the
  line is drawn around the armor too. When off the armor is stamped into the stencil while the
  player is drawn normally and the outline is masked away behind it, as it always was.
- **Glow** (Outline) with **GlowLength**: same slider shape as `WorldParticles` (0..20, step 0.05),
  6 pixels of blur radius per unit, so up to 120 pixels.
- **FishHookNametag** (NameTags): the former standalone module.

## Rebuilding

```sh
# decompile the classes (CFR), edit, then:
javac -g --release 8 -cp primordial.jar -d out \
    src/Magic/mod/s/render/PlayerESP.java \
    src/Magic/mod/s/render/NameTags.java \
    src/net/minecraft/client/renderer/entity/RendererLivingEntity.java
cp primordial.jar primordial-new.jar
zip -d primordial-new.jar 'Magic/mod/s/render/FHNametag.class'
cd out && zip ../primordial-new.jar $(find . -name '*.class' | sed 's|^\./||')
```

The jar carries `SHA-256-Digest` entries in its manifest but no signature files, so replacing
entries needs no resigning.
