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

With **SkyPvP** enabled the chain becomes:

1. AutoLeave triggers, you die, the server drops you into the lobby.
2. AutoRecharge sees the recovered health — instead of just enabling the module,
   AutoLeave hands the moment over to `SkyPvPController` and stays **off**.
3. The controller waits a second for the lobby inventory, selects the **compass** and right
   clicks it (if the compass sits outside the hotbar it is swapped into the held slot first).
4. The server opens the chest-like menu; the controller finds the **bow** inside it and clicks it,
   which sends you into SkyPvP.
5. After the world change (plus a short settle) control goes back to AutoLeave, which re-arms its
   AutoRecharge state — so AutoRecharge is what finally switches AutoLeave back on, inside the
   arena, once health is above the threshold.

This is deliberately not the same as AutoDisable: AutoDisable acts at trigger time, SkyPvP acts
*after* AutoRecharge fired, which is why it needs AutoRecharge and is only shown in the ClickGUI
while AutoRecharge is on.

Every step has a timeout. If the compass is missing, the menu never opens or there is no bow in
it, the sequence aborts, prints a chat line and AutoLeave comes back exactly like plain
AutoRecharge would have done it — the module is never left switched off.

The sequence also aborts when you turn SkyPvP off, toggle AutoLeave back on by hand, or leave the
server while it is running.

## Files

| File | Role |
| --- | --- |
| `src/Magic/mod/SkyPvPParticipant.java` | Interface a module implements to be driven by the controller (mirrors `AutoRechargeParticipant`). |
| `src/Magic/mod/SkyPvPController.java` | Separate `EventTick` driven controller holding the compass → menu → bow → join state machine. |
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
