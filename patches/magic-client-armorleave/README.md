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

## AutoFish: Leave toggle

`AutoFish.java` (`Magic/mod/s/player/AutoFish`) gained a `Leave` `BoolValue`
(off by default), independent of the existing `Afk` toggle:

- Runs every tick the module is enabled (not just while a rod is out),
  driven from the module's existing `updateEvent` listener.
- **Primary detector:** `mc.thePlayer.hurtTime > 0` — vanilla's own "just
  got hit" flag (same signal `AntiKnockBack`/`PlayerESP` already read in
  this client). Set the instant a hit registers, regardless of cause, so it
  also catches damage the fishing rod itself causes, and it doesn't miss
  hits absorbed before health visibly changes. (Originally this used a
  tick-over-tick `getHealth()` diff; switched to `hurtTime` after comparing
  against another client's AutoFish, which uses the same vanilla flag —
  it's the more standard/reliable signal and was already in use elsewhere
  in this codebase.)
- **Fallback detector:** in case a hit doesn't set `hurtTime`, a
  tick-over-tick increase in `getItemDamage()` on any of the 4 worn armor
  slots (i.e. durability going down) is treated as a hit too — armor only
  takes damage when you do.
- On a hit: self-attacks (`attackEntity(self, self)`, the same "leave"
  action `AutoLeave`/`ArmorLeave` use), then calls `leave.setValue(false)`
  to turn the toggle back off itself. No separate `AutoDisable` value was
  added for this — the requirement was for `Leave` to disarm itself
  directly, one-shot, like the standalone `Leave` module already does.
- Health/armor baselines are cleared whenever `Leave` is off (including
  right after it disarms itself) and re-established fresh the next time
  it's turned on, and again on `onDisable()`, so re-arming never compares
  against stale numbers from before it was armed.

## Files

- `ArmorLeave.java` — full source for the new module.
- `AutoFish.java` — `AutoFish` with the `Leave` toggle added.

## How it was built

1. Decompiled `Magic/mod/s/combat/AutoLeave.java` and `Magic/mod/s/player/AutoFish.java`
   (and their dependencies under `Magic/mod/`) from `primordial.jar` with
   CFR 0.152 to use as templates.
2. Wrote `ArmorLeave.java` as a new top-level class in the
   `Magic.mod.s.combat` package, swapping the health check for an armor
   durability scan of `mc.thePlayer.inventory.armorInventory`. Edited
   `AutoFish.java` in place to add the `Leave` toggle described above.
3. Compiled with `javac --release 8 -cp primordial.jar <File>.java` (the
   client is Java 8 / class file major version 52) to confirm both compile
   cleanly against the client's own classpath.
4. For `ArmorLeave`, the single new `.class` was added to a copy of the jar
   with `jar uf` and handed back to the user; `AutoFish`'s recompiled class
   was verified the same way but the jar wasn't re-sent for it — the user
   asked for the source only this time.

No manual module registration was needed for `ArmorLeave`:
`Magic.mod.Modules` discovers modules by scanning `Magic/mod/s/**` for
top-level classes that extend `Module`, so it's picked up automatically at
startup.
