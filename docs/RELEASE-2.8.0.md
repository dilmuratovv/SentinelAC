# SentinelAC 2.8.0 — combat checks and enforcing defaults

Created and maintained by **azicoo**. One JAR, `SentinelAC-2.8.0-paper.jar`, for the same Paper versions as 2.7.0 (1.21 – 26.3).

## Why

Up to 2.7.0 SentinelAC was the upstream prediction engine with Sentinel branding and policies. The engine is strong on movement, but it has no autoclicker checks, no multi-target aura check, nothing for auto-totem or crystal aura, and its default punishments only alert. 2.8.0 adds those checks and makes the defaults act.

## New checks (inside the engine, packet thread)

| Check | Catches | Evidence needed for one flag |
|---|---|---|
| `AutoClickerA` | Attack spam | More than 20 attacks within 20 client ticks, in 2 different seconds within 10 s. Excess attacks are always cancelled. |
| `AutoClickerB` | Fixed-delay autoclickers | 2 windows of 40 attacks in a row, faster than ~7 CPS, 95 % identical tick intervals. |
| `KillAuraA` | Multi-target / switch aura | Hits on different targets ≥ 90° apart within 2 client ticks, 3 times in 20 s. Later such hits are cancelled. |
| `KillAuraB` | Computed combat aim (single-target aura, aim assist) | 12 of the last 20 combat yaw changes off the player's own mouse-sensitivity step, learned outside combat and only trusted when ≥ 90 % of the player's rotations are whole steps. |
| `KillAuraC` | Machine-perfect hit timing (cooldown auras, trigger bots, slow autoclickers) | 20 hits up to 30 ticks apart whose most common interval is 4–30 ticks and covers ≥ 80 % (≥ 95 % for 4–7 ticks). |
| `AutoTotemA` | Auto totem | First click/swap after a pop within 120 ms of the client seeing it (transaction-timed, ping-independent), 2 of the last 3 pops. |
| `CrystalAuraA` | Crystal aura | 6 of the last 8 crystals broken within 50 ms of the client seeing them, with no left click in the 150 ms before. |

Thresholds are in `config.yml` under each check name.

## Defaults that act

- New default `punishments.yml`: kicks on strong evidence (Reach 30, Hitboxes 60, invalid movement 200, invalid packets 100, Knockback 80, and the Sentinel checks above), alerts otherwise. Never bans; SentinelGuard ban lines are documented in the file.
- Upgrades: a check that is in no punishment group is disabled by the engine, so on the first start of 2.8.0 the groups for the new checks are appended to an existing `punishments.yml` (comments kept, backup `punishments.yml.before-2.8.0.bak`). Groups the admin already has for these checks are respected.
- Channel policy no longer kicks minimap/tweak mods (`xaerominimap`, `xaeroworldmap`, `tweakeroo` removed); only automation and cheat namespaces are listed.
- `compatibility.allow-untested-versions: true` starts SentinelAC on a Paper release newer than the tested list (with a SEVERE warning) instead of leaving the server unprotected.

## Validation

- Build: 106 unit tests (77 previous + 29 new: click statistics including a simulation of people clicking at 7–20 CPS with 15–30 ms spread over thousands of windows, angle/step maths, punishment migration, version order, hit timing, tracking and latency), artifact verification.
- `tools/smoke_target.py` (startup, commands, reload validation, channel policy, impossible-movement probes): Paper 26.3 (ViaVersion) and Paper 1.21.11 (native and ViaVersion) passed.
- `tools/combat_probe.py` with probe clients (`tools/client/combat.cjs`) on disposable servers: Paper 26.3 (build 230) + ViaVersion/ViaBackwards 5.12.2 with 26.1-protocol clients, and native Paper 1.21.11 (build 132) with 1.21.11 clients. Same result on both:

| Scenario | Behaves like | Expected | Result |
|---|---|---|---|
| clicker25 | 25 CPS autoclicker | AutoClickerA | flagged, kicked |
| clicker10 | exact 10 CPS autoclicker | AutoClickerB | flagged |
| human | ~9 CPS, 25 ms spread | nothing | no Sentinel flag |
| aura | switching between two targets every tick | KillAuraA | flagged, kicked after 8 attacks |
| switcher | person fighting two targets with a smooth turn | nothing | no Sentinel flag |
| autototem | swap on the pop packet | AutoTotemA | flagged |
| manualtotem | swap 350 ms after the pop | nothing | no Sentinel flag |
| crystalaura | break on the spawn packet | CrystalAuraA | flagged |
| crystalhuman | break 260 ms after the spawn | nothing | no Sentinel flag |

The probe clients also raise upstream `Timer`/`Post` flags; those come from the Node.js timer and packet order of the probe, not from the new checks.

## Real cheat client test (Meteor Client)

`tools/realclient_probe.py`: Paper 1.21.11 (build 132) with this JAR, a real Minecraft 1.21.11 client (Fabric 0.20.1) with **Meteor Client 1.21.11-86** in a virtual X display, a standing target player. Cheat modules were switched on with Meteor's own `.toggle` / `.settings` commands; legit baselines used real mouse/keyboard input through the genuine client, aiming with the mouse in whole sensitivity steps. Same final JAR for every row.

| Legit baseline (real client, no module) | Result |
|---|---|
| idle, walking | clean |
| clicking ~8 CPS, ~13 CPS, butterfly ~17 CPS on a target | clean |
| 1.9+ sword fight against a moving target (mouse tracking, cooldown timing) | clean |
| hitting while aiming 11° beside the target | clean |
| manual totem refill (F key ~350 ms after the pop) | clean |

| Meteor module (defaults unless noted) | Caught by | Kicked |
|---|---|---|
| Kill Aura (single target, rotate Always) | KillAuraB | yes |
| Kill Aura, custom hit delay 4 | KillAuraB | yes |
| Kill Aura, rotate None | KillAuraC, Hitboxes | yes |
| Auto Clicker | AutoClickerB | yes |
| Auto Totem | AutoTotemA | yes |
| Criticals (packet) | GroundSpoof, Simulation, Timer | yes |
| Hitboxes | Hitboxes | alert |
| Velocity | AntiKB | alert |
| Flight | Simulation, GroundSpoof | yes |
| Spider | Simulation | yes |
| Timer ×3 and ×1.5 | Timer, TimerLimit | yes |
| Speed, Step, No Fall | Simulation / GroundSpoof | alert |
| Crystal Aura | not exercised: the module never placed a crystal in the bench, so it is only covered by the protocol probe above | — |

The real-client data also changed the design: the first KillAuraB missed Meteor's aura because the aura's own rotations corrupted the sensitivity estimate it relied on (0 % flag); learning the step outside combat fixed it (71 % of aura yaw changes off-step against 0 % for a person). KillAuraC came from the measured hit timing (90 % of aura intervals exactly 12 ticks, 19 % for a person).

## Real cheat client test (LiquidBounce)

Same bench with **LiquidBounce 0.43.0** (Minecraft 26.3, Fabric API 0.165.0, Fabric Language Kotlin 1.14.1) on Paper 26.3 (build 230) + ViaVersion/ViaBackwards 5.12.2, default module settings. LiquidBounce is built to evade the upstream engine: GCD-corrected and smoothed rotations, human-like click and inventory timing.

| Legit baseline (real client) | Result |
|---|---|
| idle, walking, clicking ~8 CPS, sword fight, target out of reach, sprint fight | clean |
| manual totem refill (standing and while walking) | clean |

| LiquidBounce module (defaults) | Caught by | Kicked |
|---|---|---|
| KillAura | KillAuraC (88 % of hits exactly 13 ticks apart) | yes |
| TriggerBot | KillAuraC | yes |
| AutoClicker (hold) | KillAuraC (96 % of clicks exactly 5 ticks apart) | yes |
| Reach | Reach | yes |
| Hitbox | Hitboxes | yes |
| Criticals | Simulation, GroundSpoof, Timer | yes |
| Timer | Timer, Simulation | yes |
| Fly, Velocity, NoFall, Step, Spider | Simulation / GroundSpoof / AntiKB | flags |
| Aimbot (player clicks) | — | **not caught** |
| Backtrack, TickBase (defaults) | — | **not caught** |
| Offhand (auto totem) | — | **not caught**: refills 345–400 ms after the pop with an inventory swap click, the same timing (256–406 ms) and the same packets as a person using hover + F |
| Speed (default mode), KeepSprint | — | **not caught** |

What the LiquidBounce run changed: KillAuraB misses its GCD-corrected rotations (1 % off-step), so cooldown timing became the key signal; the first real run showed a hole between AutoClickerB (≤ 3 ticks) and KillAuraC (≥ 8 ticks) that LiquidBounce's 5-tick clicker sat in, so KillAuraC now covers 4–30 ticks with a 95 % bar for 4–7 ticks; a simulation test then found and removed a statistical bias at the 4-tick edge before release.

## Aimbot, Backtrack and ping

`tools/realclient_probe.py --rtt/--jitter/--spike` puts a latency proxy between the real client and the server (half the round trip each way, jitter, periodic lag spikes). The simulated person now also reacts like one: it sees the target only after the server sent it and the client received it, plus a 170 ± 30 ms visual-motor reaction (an earlier version reacted within 0–50 ms of the client seeing the target, which no person can, and TrackingA rightly flagged it).

Two new checks came from traces of LiquidBounce 0.43.0 under that network:

- **TrackingA (aimbot / aura):** the target's per-tick direction change, as the client has acknowledged it, against the player's per-tick yaw change. LiquidBounce Aimbot (GCD-corrected and smoothed, so KillAuraB cannot see it) peaks at a lag of 1 tick with correlation 0.58; a person tracking the same target stays around 0.07. Both sides are timed in the client's own frame, so ping cancels out.
- **BacktrackA:** LiquidBounce Backtrack holds every incoming packet (transactions included) for 100–150 ms while fighting. Fight-minus-calm ping of the same player: Backtrack +16…+28 ms median and +71…+108 ms at the 75th percentile; a person under the same network: −23 ms / −9 ms. Quantiles ignore lag spikes, and the comparison is with the player's own ping.

| Network | Legit baselines (real client) | LiquidBounce Aimbot | LiquidBounce Backtrack | LiquidBounce KillAura / TriggerBot |
|---|---|---|---|---|
| 120 ms ± 15, 250 ms spikes | clean (sword, retreating target, clicking, sprint fight) | TrackingA, kicked | BacktrackA, kicked | KillAuraC + TrackingA, kicked |
| 250 ms ± 40, 400 ms spikes | clean (idle, walk, sword, retreating target, clicking, sprint fight, totem while moving) | TrackingA, kicked | BacktrackA, kicked | — |

Meteor Client under 120 ms ± 15 with spikes: legit clicking, butterfly, sword, retreating target and totem clean; KillAura, KillAura without rotations, AutoClicker, AutoTotem, Timer ×1.5 and Flight all kicked.

## Limits

- The movement engine is unchanged upstream code: public bypass modes written for it still matter, and fixes must be pulled from upstream.
- Two cheat clients (Meteor, LiquidBounce) were tested with a real client. Not caught with LiquidBounce defaults: TickBase, Offhand (indistinguishable from hover + F at human timing), Speed, KeepSprint. An aimbot that adds a human-like 150 ms+ delay, or a backtrack that also delays outside fights, would avoid TrackingA / BacktrackA. A kill aura that randomises its hit timing (cooldown off, human clicker) and corrects rotations to the sensitivity grid avoids both KillAuraB and KillAuraC.
- The tick-precise parts need clients that send a packet every tick (1.21.2+ clients on 1.21.2+ servers, and 1.8). On older combinations AutoClickerA and KillAuraA fall back to wall time and AutoClickerB is skipped.
- Legit baselines were a real client driven by a program (simulated network up to 250 ms ± 40 ms with lag spikes); real player populations were not measured. BacktrackA compares a player's fighting ping with their own calm ping: a connection that gets worse only while fighting would look the same, so it kicks after 3 flags, not 2. Run alert-only for a while on a staging server before relying on the kicks.
