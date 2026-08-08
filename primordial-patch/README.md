# primordial patch — AutoLeave › SkyPvP

Source of the classes that get injected into `primordial.jar` (Minecraft 1.8.9, MCP names).
The jar itself is not kept in this repository — it is the input of `build.sh`.

## What the SkyPvP setting does

`AutoLeave` already had two sub settings:

* **AutoDisable** — turns the module off right after it hit you to death.
* **AutoRecharge** — turns it back on once your health is above the `Health` threshold again.

The problem that **SkyPvP** solves: after AutoLeave triggers you get sent to the lobby with full
health, AutoRecharge immediately flips AutoLeave back on while you are still standing in the
lobby, and you have to walk back into SkyPvP by hand (compass → menu → bow).

The rejoin itself is always the same: wait out the death and the respawn, select the **compass**
and right click it (if it sits outside the hotbar it is swapped into the held slot first), wait
for the chest-like menu the server opens, click the **bow** inside it, then wait until the player
is actually loaded in on the other side.

What differs is where it hooks in and what happens afterwards:

| AutoDisable | AutoRecharge | SkyPvP |
| --- | --- | --- |
| on | on | starts **after** the recharge fired: AutoLeave stays off, gets you back into the mode and is switched on again once you are in, alive and above the health threshold. |
| on | off | starts right at the trigger, after AutoDisable turned the module off: it only walks you back into the mode, AutoLeave stays off (that is what AutoDisable alone means). |
| off | — | the setting is hidden: without AutoDisable the module never turns itself off, so there is nothing to hook into. |

This is deliberately not the same as AutoDisable: AutoDisable acts at trigger time and only turns
the module off, SkyPvP is what walks you back in — and with AutoRecharge it takes the recharge
moment over instead of coming back in the lobby.

The module is switched back on by the controller itself as soon as the sequence is over (and only
when AutoRecharge is on), not by waiting for another recharge pass — waiting for one is what left
AutoLeave switched off after a server switch.

Every step has a timeout, the whole sequence has a hard deadline on top of that, and
`handleAutoRecharge` has a watchdog that unparks the module if the controller ever drops a
sequence without saying so. If the compass is missing, the menu never opens or there is no bow in
it, the sequence aborts, prints a chat line, and with AutoRecharge on AutoLeave comes back exactly
like plain AutoRecharge would have done it — the module is never left switched off by accident.

The wait after the bow click survives the gap where the client has no world and no player at all,
which is what a BungeeCord style server switch looks like from the client side. A warp that keeps
the same world is covered too (after ~6 s without a world change the sequence stops waiting for
one).

The sequence also aborts when you turn SkyPvP off, toggle AutoLeave back on by hand, or leave the
server while it is running.

## Files

| File | Role |
| --- | --- |
| `src/Magic/mod/SkyPvPParticipant.java` | Interface a module implements to be driven by the controller (mirrors `AutoRechargeParticipant`). |
| `src/Magic/mod/SkyPvPController.java` | Separate `EventTick` driven controller holding the respawn → compass → menu → bow → join → return state machine. |
| `src/Magic/mod/s/combat/AutoLeave.java` | The module: new `SkyPvP` `BoolValue` plus the hand-off in `handleAutoRecharge`. |

`AutoRechargeController` / `AutoRechargeParticipant` are unchanged and stay in the jar.

## Build

```bash
./build.sh /path/to/primordial.jar [/path/to/output.jar]
```

Compiles the sources above against the input jar (`-source 8 -target 8`, the jar is Java 8
bytecode) and writes a copy of the jar with those class entries replaced/added. All other entries
are copied untouched — the jar is unsigned, so the stale `META-INF/MANIFEST.MF` digests are not a
problem.

Config files stay compatible: `CGui.loadNew()` skips value lines it does not know and leaves
unknown-to-the-file settings at their default, so an old config simply loads `SkyPvP = false`.
