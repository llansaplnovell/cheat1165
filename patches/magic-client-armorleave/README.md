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

## AutoFish: Guard toggle (anti-steal)

`AutoFish` also gained a `Guard` `BoolValue` (off by default) plus a
`Range` `NumberValue<Float>` (default `1.0`, `0.5-5.0`, only shown while
`Guard` is on): while enabled, AutoFish won't reel in as long as another
player is standing on or near your **fishing line** - not just at the
bobber - close enough that they could grab the loot themselves the instant
it lands.

This isn't a hard cancel — `pullBack()` (triggered by the bite packets in
`onReceive`, both the hook velocity packet and the splash-sound path) checks
`isBobberContested()` first. If contested, instead of reeling it just
records `pendingReel = true` and the pull strength it would have used, and
returns without touching the rod. From then on `updatePendingReel()` runs
every tick (from `updateEvent`, alongside `updateLeave()`) and reels the
moment `isBobberContested()` goes false, using the recorded pull strength -
so the catch is still grabbed the instant the area clears, rather than
being lost because the one bite packet that would have triggered a normal
reel already passed. If the hook itself disappears while still pending
(catch missed, line timed out, etc.) the pending state is just dropped.
The original reel body (the afk-aware double right-click + debug log) was
factored out into `reelIn()` so both the immediate and the deferred path
share it instead of duplicating the logic.

**Detection, revised.** The first version checked distance to the hook
entity only, and that's not what the line actually is: the fishing line
itself isn't an entity — it's a purely client-side render between the rod
and the hook, with no position or hitbox the client's entity list knows
about, so "is someone standing on my line" can't be looked up, only
computed. `isBobberContested()` now does that directly:

- Approximates the line as the straight segment from the rod tip (the
  local player's eye position, `getPositionEyes(1.0f)`, is the closest
  thing available to a rod-tip position) to the hook's real coordinates.
- For every other player (`ClientUtils.getPlayers()`, local player always
  skipped), tests their actual bounding box — expanded by `Range` — against
  that segment using `AxisAlignedBB.calculateIntercept`, vanilla's own
  ray-vs-box routine. This is the exact primitive `NameTags` already uses
  in this client to figure out which hook the crosshair is hovering, just
  pointed at players instead of the mouse ray. This is what catches someone
  standing partway along the line rather than only right on the bobber.
- `isNearHook()` — the hook's own box expanded by `Range`, checked for
  overlap with the player's box — is kept as a belt-and-braces fallback for
  one quirk in `calculateIntercept`: a ray whose start point is already
  inside the target box can come back with no intercept, which would
  otherwise let someone standing exactly on top of the hook slip through.
  `isBobberContested()` is true if either check hits.
- `updateGuardLog()` runs once a tick and prints a chat line
  (`ClientUtils.debug`) whenever the contested state flips, purely so the
  detection is visible while testing/tuning `Range` instead of only
  inferring it from whether a reel got delayed.

One thing worth flagging for testing: the local player is always excluded
from the contest check (guarding against yourself makes no sense), so
standing at your own bobber solo won't trigger anything — it needs a
second character/account nearby to actually verify.

**On "is this just a circle again":** the pre-line-fix version really was
a plain circle around the hook's coordinate (`getDistanceToEntity(hook) <
range`), so a player who couldn't plausibly reach the catch could still
trip it if they happened to be within `range` of that one point. The
segment version isn't that: `isBlockingLine()`'s capsule spans the *whole*
rod-tip-to-hook segment, so a player has to actually be near some point
along that segment, not just near the hook's coordinate, to trigger it.
The only remaining circular check, `isNearHook()`, is scoped to the hook's
own box (the capsule's working end-cap), not some unrelated point. To make
this verifiable rather than just asserted, `findContestingReason()` now
returns *which* check fired and who triggered it, and both
`updateGuardLog()` and `pullBack()`'s debug lines include it — e.g. `Guard:
line is contested (on the line, Steve), holding the reel.` — so it's
directly visible in chat during testing which of the two checks matched.

**Friends are exempt.** `findContestingReason()` now skips any player
`Magic.utils.Friend.FriendManager.isFriend(name)` returns true for — the
same friends list `KillAura` already refuses to target and `MCF`/`NameTags`
already check against, so this reuses the client's existing friend concept
rather than adding a second one. The skip happens per-player inside the
loop (`continue`, not an early return), so it only removes that one friend
from consideration — it doesn't turn Guard off for the whole tick. A friend
alone on the line never contests it; a friend and a stranger on the line at
the same time still contests it, because the stranger is still checked and
still matches on their own.

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
