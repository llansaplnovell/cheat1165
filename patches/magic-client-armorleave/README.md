# ArmorLeave (Magic client, `primordial.jar`)

This targets the compiled client attached to this session
(`Magic/mod/s/combat/*.class`), not the Forge MDK skeleton the rest of this
repo builds — the two are unrelated codebases. Checked in here only so the
change is versioned and reviewable.

## What this is

A brand new module, `ArmorLeave`, sitting next to the existing `AutoLeave`
module in the `Combat` category. `AutoLeave` itself is **untouched** — same
class, same behavior, byte-identical `.class` file to the one already in
`primordial.jar`.

`ArmorLeave` mirrors `AutoLeave`'s exact structure (self-attack leave +
`AutoDisable` + `AutoRecharge` + `SkyPvP`), just built around armor
durability instead of health:

- **`Durability`** (`NumberValue<Integer>`, default `20`, range `1-100`) —
  the trigger threshold. The module fires (self-attacks, i.e. leaves) the
  moment any currently worn armor piece's remaining durability
  (`getMaxDamage() - getItemDamage()`) drops to or below this value. Empty
  armor slots don't count — only pieces you're actually wearing are checked.
- **`AutoDisable`** — disables `ArmorLeave` right after it fires, same as
  `AutoLeave`'s.
- **`AutoRecharge`** — re-enables `ArmorLeave` once your worn armor's
  durability is back above the threshold (e.g. after repairing/swapping
  gear), same latch/wait logic as `AutoLeave`'s.
- **`SkyPvP`** — same compass/bow rejoin sequence via `SkyPvPController`,
  gated on armor durability being safe instead of health.

Both modules can run independently and simultaneously (each is a separate
`Module`, each with its own `AutoDisable`/`AutoRecharge`/`SkyPvP` toggles).
They share the existing `AutoRechargeController` and `SkyPvPController`
singletons — those already iterate every registered participant module, so
no changes were needed there. The one caveat: `SkyPvPController` only runs
one rejoin sequence at a time, so if both `AutoLeave` and `ArmorLeave` want
to rejoin in the same moment, whichever asks first wins and the other's
`requestRejoin` call simply returns `false` (same as it always has for any
two SkyPvP-participant modules).

## Files

- `ArmorLeave.java` — full source for the new module.

## How it was built

1. Decompiled `Magic/mod/s/combat/AutoLeave.java` (and its dependencies
   under `Magic/mod/`) from `primordial.jar` with CFR 0.152 to use as the
   template.
2. Wrote `ArmorLeave.java` as a new top-level class in the same
   `Magic.mod.s.combat` package, swapping the health check for an armor
   durability scan of `mc.thePlayer.inventory.armorInventory`.
3. Compiled with `javac --release 8 -cp primordial.jar ArmorLeave.java` (the
   client is Java 8 / class file major version 52).
4. Added the single new `.class` to a copy of the jar with `jar uf`. No
   other class — `AutoLeave.class` included — was modified.

No manual module registration was needed: `Magic.mod.Modules` discovers
modules by scanning `Magic/mod/s/**` for top-level classes that extend
`Module`, so `ArmorLeave` is picked up automatically at startup.

The rebuilt jar isn't committed here (large binary, unrelated to this
repo's Gradle project) — it was sent directly to the user.
