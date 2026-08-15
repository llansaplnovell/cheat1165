# ArmorLeave for AutoLeave (Magic client, `primordial.jar`)

This patch targets the `AutoLeave` module inside the attached compiled client
(`Magic/mod/s/combat/AutoLeave.class`), not the Forge MDK skeleton the rest of
this repo builds — the two are unrelated codebases. It's checked in here only
so the change is versioned and reviewable.

## What changed

`AutoLeave` used to trigger only on low health (`Health` value). It now also
supports armor durability as a second, independent trigger, and every
dependent behaviour (`AutoDisable`, `AutoRecharge`, `SkyPvP`) has been
generalized to react to whichever trigger fired instead of only health.

- **`ArmorLeave`** (new `BoolValue`, off by default) — when enabled, AutoLeave
  also fires the moment *any* currently worn armor piece's remaining
  durability drops to or below the `Durability` threshold.
- **`Durability`** (new `NumberValue<Integer>`, default `20`, range `1-100`,
  only visible while `ArmorLeave` is on) — the durability threshold. Default
  matches the requested "≤ 20 durability".
- **`AutoDisable`** — unchanged in behavior, but now disables the module for
  an armor-durability trigger exactly like it already did for a health
  trigger.
- **`AutoRecharge`** — previously re-enabled once `health > Health`. Now
  re-enables once *every* active trigger has recovered: health is back above
  its threshold, and, if `ArmorLeave` is on, no worn armor piece is still at
  or under the durability threshold (e.g. after repairing or swapping in
  fresh armor).
- **`SkyPvP`** — the rejoin sequence's "ready" check
  (`isSkyPvPRejoinReady`) used the same health-only condition; it now uses
  the same generalized recovery check, so it won't click back in while your
  armor is still critically damaged.

Implementation detail: the old `currentHealth() > threshold()` /
`currentHealth() <= threshold()` checks scattered through the module were
replaced with two helpers, `shouldLeave()` (any trigger active) and
`isSafeState()` (every enabled trigger has recovered), plus
`isArmorDurabilityLow()` which scans `mc.thePlayer.inventory.armorInventory`
(4 slots) for any damageable piece with `getMaxDamage() - getItemDamage() <=
durabilityThreshold()`. Empty slots don't count against you — nothing to
protect there, so `ArmorLeave` only reacts to armor you're actually wearing.

## Files

- `AutoLeave.java` — full patched source, drop-in replacement for
  `Magic/mod/s/combat/AutoLeave.java`.
- `AutoLeave.diff` — unified diff against the original decompiled class.

## How it was built

1. `Magic/mod/s/combat/AutoLeave.class` (and its sibling classes under
   `Magic/mod/`) was decompiled from the uploaded `primordial.jar` with CFR
   0.152 to recover readable source and confirm the existing
   `AutoDisable`/`AutoRecharge`/`SkyPvP` wiring.
2. `AutoLeave.java` was rewritten with the `ArmorLeave`/`Durability` values
   and the generalized trigger/recovery helpers described above.
3. Recompiled with `javac --release 8 -cp primordial.jar AutoLeave.java` —
   the class was Java 8 (major version 52), so this reproduces the original
   bytecode format bit-for-bit compatible with the rest of the jar.
4. The single recompiled `.class` was swapped back into a copy of the jar
   with `jar uf`. No other class in the jar was touched.

The rebuilt jar itself isn't committed here (large binary, unrelated to this
repo's Gradle project) — it was sent directly to the user.
