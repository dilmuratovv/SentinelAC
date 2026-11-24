<div align="center">

# SentinelAC

### Fair play starts with a stronger server.

A Minecraft anticheat project by **azicoo**, built around server-side prediction, configurable enforcement, and practical tools for server administrators.

[![Build](https://github.com/dilmuratovv/SentinelAC/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/dilmuratovv/SentinelAC/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/dilmuratovv/SentinelAC?color=ec4899&label=release)](https://github.com/dilmuratovv/SentinelAC/releases)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
[![Paper](https://img.shields.io/badge/paper-1.21%20%E2%80%93%2026.3-0ea5e9.svg)](#compatibility)
[![Author](https://img.shields.io/badge/author-azicoo-8b5cf6.svg)](https://github.com/dilmuratovv/SentinelAC)

[Download](https://github.com/dilmuratovv/SentinelAC/releases) · [Installation](#installation) · [Commands](#commands) · [Compatibility](#compatibility) · [Validation](docs/VALIDATION.md)

</div>

---

## About

SentinelAC combines a prediction-based anticheat engine with server policy controls and bounded diagnostics. **One standalone JAR runs on every supported Paper version (1.21 to 26.3)**, and checks the server version at startup. Movement, combat, packet behavior, and server rules are managed through a shared lifecycle and the `/sac` command tree.

The JAR is compiled once against the oldest supported Paper API; CI then links it against, and boots it on, every supported version before a release is published. The engine is included in the JAR.

It is part of the [Sentinel suite](#sentinel-suite).

**Created and maintained by azicoo.**

## Sentinel suite

Five plugins by **azicoo**, each with its own repository and release. Every one works on its own.

| Plugin | What it does |
|---|---|
| [SentinelAC](https://github.com/dilmuratovv/SentinelAC) | Anticheat: prediction-based movement, combat and packet checks. |
| [SentinelAD](https://github.com/dilmuratovv/SentinelAD) | Anti-dupe: a global item ledger that removes duplicated items. |
| [SentinelAE](https://github.com/dilmuratovv/SentinelAE) | Anti-ESP: hidden players and armor stands are never sent to the client. |
| [SentinelAX-ray](https://github.com/dilmuratovv/SentinelAX-ray) | Anti-xray: Paper's native engine set up per world, plus mining analysis. |
| [SentinelGuard](https://github.com/dilmuratovv/SentinelGuard) | Moderation: bans, mutes, history, `/sus`; collects the flags of AC, AD and AX-ray. |

Together: SentinelGuard adds the findings of SentinelAC, SentinelAD and SentinelAX-ray to `/sus`, and `/sg status` shows the state of SentinelAE and SentinelAX-ray. All five are GPL-3.0 and run on Paper 1.21 to 26.3 with one JAR each (SentinelGuard also has a Velocity JAR).

## Features

| Area | What SentinelAC provides |
|---|---|
| **Movement prediction** | Server-side movement simulation, with checks for impossible movement, timing, and velocity behavior. |
| **Combat & packets** | Reach, hitbox, knockback, packet-order, and malformed-packet checks from the integrated engine. |
| **Sentinel combat checks** | Packet-level checks the upstream engine does not have: attack-rate limit and machine-regular clicking (autoclicker), fast multi-target switching and off-sensitivity combat aim (aura), ping-independent reaction timing for auto-totem and crystal aura. |
| **Channel blacklist** | Configurable exact-channel and namespace rules, checked on join and later channel registrations, with optional kicks. |
| **Action limits** | Sliding-window crystal and respawn-anchor policies, including a shared crystal budget for both hands. |
| **Staff tools** | Alerts, profiles, recent findings, persistent history, prediction timing, and configuration reloads. |
| **Bounded diagnostics** | Limited queues and in-memory history, rotating disk logs, and local debug dumps. |
| **Optional Discord alerts** | Administrator-configured webhooks with a bounded queue, finite request lifetime, and limited rate-limit retries. |
| **Source transparency** | Pinned engine/API sources, a documented build process, automated tests, and artifact verification. |

## Installation

1. Download `SentinelAC-<version>-paper.jar` from [Releases](https://github.com/dilmuratovv/SentinelAC/releases). Check the supported server and Java versions below.
2. Back up your server, configuration, and history databases.
3. Remove previous SentinelAC builds and any separately installed GrimAC engine JAR. SentinelAC already includes its engine.
4. Place the SentinelAC JAR in your server's `plugins/` directory and restart.
5. Check the startup log, then run `/sac info`.

Configuration lives in **`plugins/SentinelAC/`**. Review the generated defaults before copying settings from an older build. Automatic migration of configurations or databases from previous modified binaries is not guaranteed.

For protocol translation, install the compatible **ViaVersion and ViaBackwards plugins on the backend Paper server**. Proxy-side ViaVersion is outside the current support scope.

## Commands

**Main command:** `/sac`  
**Aliases:** `/sentinel`, `/sentinelac`

| Command | Purpose |
|---|---|
| `/sac help` | View command help. |
| `/sac info` | Show build, translation-plugin, and diagnostic status. |
| `/sac alerts` · `/sac verbose` | Control staff notifications. |
| `/sac profile <player>` | Inspect tracked client information. |
| `/sac channels <player>` | Inspect advertised plugin channels and matching rules. |
| `/sac recent <player>` | View recent local findings. |
| `/sac history <player>` | Browse stored sessions and violations. |
| `/sac perf` | View prediction timing. |
| `/sac reload` | Reload configuration with Sentinel policy validation. |
| `/sac version` | Show the build version. |
| `/sac log <id>` · `/sac dump` | Save diagnostics locally. |

<details>
<summary><strong>Permissions</strong></summary>

`sentinel.admin` grants administrative commands. Exemption permissions default to **false**; administrative access does not automatically exempt a player from checks.

| Permission | Access |
|---|---|
| `sentinel.help`, `sentinel.info`, `sentinel.version` | Help and status commands. |
| `sentinel.alerts`, `sentinel.verbose` | Staff notifications. |
| `sentinel.profile` | Player profiles and channels. |
| `sentinel.history` | Recent findings and stored history. |
| `sentinel.performance` | Prediction timing. |
| `sentinel.reload` | Configuration reload. |
| `sentinel.log`, `sentinel.dump` | Diagnostic files. |
| `sentinel.exempt` | Exemption from engine checks and Sentinel policies. |

</details>

## Compatibility

Release **2.8.0** is a single JAR, `SentinelAC-2.8.0-paper.jar`, for these exact Paper versions. Each one is covered by an API link check, native and Via server probes and the unit tests before publication.

| Paper version | Java (needed by the server) | Tested Paper build/channel |
|---|---|---|
| 1.21 | 21 | 130 (STABLE) |
| 1.21.1 | 21 | 133 (STABLE) |
| 1.21.3 | 21 | 83 (STABLE) |
| 1.21.4 | 21 | 232 (STABLE) |
| 1.21.5 | 21 | 114 (ALPHA) |
| 1.21.6 | 21 | 48 (STABLE) |
| 1.21.7 | 21 | 32 (STABLE) |
| 1.21.8 | 21 | 60 (STABLE) |
| 1.21.9 | 21 | 59 (ALPHA) |
| 1.21.10 | 21 | 130 (STABLE) |
| 1.21.11 | 21 | 132 (STABLE) |
| 26.1.1 | 25 | 29 (ALPHA) |
| 26.1.2 | 25 | 74 (STABLE) |
| 26.2 | 25 | 135 (STABLE) |
| 26.3 | 25 | 230 (STABLE) |

The JAR itself is Java 21 bytecode, so it runs on Java 21 (Paper 1.21.x) and Java 25 (Paper 26.x). Install the same file on all of them.

**Unavailable names:** Paper has no published 1.21.2 or exactly 26.1 server build. The 26.1 line is covered through its published 26.1.1 and 26.1.2 versions. Any version not in the table, newer or older, is refused at startup with a clear message. ALPHA labels above describe the official Paper builds and are not a claim of Paper stability.

ViaVersion/ViaBackwards 5.12.2 are used in the translation tests. Native wire-protocol tests run where a matching client codec is available; full gameplay compatibility is not inferred from login and enforcement probes. Other server versions, Paper before 1.21 and Folia are outside this matrix.

See [Validation](docs/VALIDATION.md) and the release's `VERIFICATION.json` for exact coverage and staging limits.

## Configuration & enforcement

- **Channel rules:** Match exact identifiers or namespaces. A matching advertised channel can trigger a kick. Clients can hide channels, so this feature is not a complete inventory of installed mods.
- **Action limits:** Excess crystal or anchor actions are cancelled according to server policy. Exceeding a rate limit alone is not proof of cheating.
- **Punishments:** Since 2.8.0 the default `punishments.yml` is a maximum-enforcement, kicks-only profile: every group kicks (movement 80, knockback 30, invalid packets 40, packet order 60, reach 10, hitboxes 20, other illegal actions 30, the Sentinel combat checks after 2–3 flags) and nothing bans. `Reach.enable-post-packet` is on by default. Upgrading servers keep their own file: the groups for the new checks are appended to it once (backup `punishments.yml.before-2.8.0.bak`), because a check that is in no group stays disabled.
- **Untested Paper versions:** a Paper release newer than the tested list is refused, unless `compatibility.allow-untested-versions: true` (SEVERE warning at startup).
- **Reloads:** Invalid Sentinel policy settings are rejected before application. Diagnostic buffer/log-size changes require a restart; inspect the console if an engine subsystem fails to reload.
- **Diagnostics:** Logs rotate by size and count. Debug dumps remain on the server. Webhooks are disabled by default, and no bStats or upstream update/paste requests are started by SentinelAC.

Test new builds and punishment thresholds on a staging server with your plugin set. Detection coverage and resource use depend on gameplay, network conditions, and configuration; zero lag, zero false positives, and universal cheat detection are not guaranteed.

## Sentinel combat checks (2.8.0)

All of them run inside the engine on the packet thread, use client ticks or transaction timing instead of wall time where possible, and need a repeated pattern before a single flag. Thresholds are in `config.yml` under each check name; `sentinel.exempt.<check>` exempts one player.

| Check | What it catches | Flags when | Default action |
|---|---|---|---|
| `AutoClickerA` | Attack spam above a CPS limit | More than `max-cps` (20) attacks within 20 client ticks, in 2 different seconds within 10 s. Attacks above the limit are always cancelled. | alert, kick at 30 |
| `AutoClickerB` | Fixed-delay autoclickers | Two windows of 40 attacks in a row, both faster than ~7 CPS and with 95 % identical tick intervals | alert, kick at 2 |
| `KillAuraA` | Multi-target / switch aura | Hits on different targets ≥ 90° apart within 2 client ticks, 3 times in 20 s; later such hits are cancelled | alert, kick at 3 |
| `KillAuraB` | Aim computed by a cheat during combat (single-target aura, aim assist) | 12 of the last 20 combat yaw changes fall between the player's own mouse-sensitivity steps, learned outside combat and only trusted when ≥ 90 % of the player's own rotations are whole steps (trackpads, zoom and cinematic camera are never judged) | alert, kick at 2 |
| `KillAuraC` | Cooldown-paced auras, trigger bots and slow autoclickers (machine-perfect hit timing) | 20 hits 4–30 ticks apart with ≥ 80 % identical intervals (≥ 95 % when the common interval is 4–7 ticks) | alert, kick at 2 |
| `TrackingA` | Aimbot / aura tracking | The player's yaw follows the target's movement (as the client sees it) within 0–1 client tick: correlation ≥ 0.45 over 200 fighting ticks and 0.1 stronger than at a human reaction lag (3–5 ticks). Ping-independent | alert, kick at 2 |
| `BacktrackA` | Backtrack (holding incoming packets while fighting) | The player's own ping while fighting is ≥ 8 ms (median) and ≥ 50 ms (75th percentile) above their own calm ping, over 400 fighting samples | alert, kick at 3 |
| `AutoTotemA` | Auto totem | The first click/swap after a totem pop comes ≤ 120 ms after the client saw the pop (ping-independent), 2 of the last 3 pops | alert, kick at 2 |
| `CrystalAuraA` | Crystal aura | 6 of the last 8 crystals broken ≤ 50 ms after the client saw them, with no left click in the 150 ms before | alert, kick at 2 |

`tools/combat_probe.py` runs these against a disposable localhost server with protocol probe clients (`tools/client/combat.cjs`). `tools/realclient_probe.py` runs a **real Minecraft client with a cheat client mod** (Meteor Client) in a virtual display against a disposable server: modules are switched with the cheat client's own chat commands, and the legit baselines use real mouse and keyboard input (XTEST) through the genuine client. `-Dsentinel.trace=<dir>` (development only) records every rotation and attack for `tools/analyze_trace.py`.

Limits: AutoClickerB, KillAuraC, AutoTotemA's tick timing and CrystalAuraA's idle rule assume clients that send a packet every tick (1.21.2+ clients on 1.21.2+ servers, and 1.8). On older combinations AutoClickerA and KillAuraA fall back to wall time and AutoClickerB/KillAuraC are skipped. Cheats that randomise their hit timing AND correct their rotations to the sensitivity grid are much harder to see; tested with real clients, also behind a simulated network (120 ms and 250 ms round trip with jitter and lag spikes): Meteor Client (all tested modules caught) and LiquidBounce (KillAura, TriggerBot, AutoClicker, Aimbot, Backtrack, Reach, Hitbox, Criticals, Timer and the movement modules caught; TickBase, Offhand, Speed and KeepSprint not caught) — see docs/RELEASE-2.8.0.md.

## Build from source

Use **Maven 3.9+**, **Python 3**, **Git** and **JDK 21** (a newer JDK also works; the JAR targets Java 21).

```sh
git clone --recurse-submodules https://github.com/dilmuratovv/SentinelAC.git
cd SentinelAC
git submodule update --init --recursive
mvn -B -ntp clean verify
python3 tools/verify_artifact.py
```

Output: `plugin/target/SentinelAC-2.8.0-paper.jar`. The build compiles against the Paper 1.21 API. The `paper-<version>` Maven profiles only select another API to check the JAR against, they do not produce a different JAR. Never install multiple Sentinel builds on the same server.

To check the JAR against another Paper version (JDK 25 for 26.x):

```sh
python3 tools/api_compat.py --extract-only --jar plugin/target/SentinelAC-2.8.0-paper.jar --refs-cache refs.tsv
mvn -B -ntp -q -f tools/api-classpath/pom.xml dependency:build-classpath -Dpaper.version=1.21-R0.1-SNAPSHOT -Dmdep.outputFile="$PWD/baseline.txt"
mvn -B -ntp -q -f tools/api-classpath/pom.xml dependency:build-classpath -Dpaper.version="$(python3 tools/paper_version.py 26.3)" -Dmdep.outputFile="$PWD/target.txt"
python3 tools/api_compat.py --jar plugin/target/SentinelAC-2.8.0-paper.jar --refs-cache refs.tsv --baseline-classpath "$(cat baseline.txt)" --classpath "$(cat target.txt)"
```

<details>
<summary><strong>Build architecture</strong></summary>

- `upstream/engine` and `upstream/api` preserve the pinned original sources.
- `tools/generate_engine.py` produces the Sentinel source port and explicit modifications during the build.
- `plugin/` compiles the engine, APIs, compatibility shim, and Sentinel modules from source.
- `runtime/` extracts third-party libraries and protocol assets from a hash-verified distribution. Engine/API bytecode and the source-built shim are excluded from that carrier.
- Artifact verification checks required resources, codec metadata, core bytecode, duplicate entries, legal notices, and active branding.

The release pipeline requires **the automated tests**, artifact verification, an API link check and localhost server/protocol probes on **every supported Paper version** with the same JAR before publication. The ray arithmetic change is checked against the original over 25,000 random cases and boundary cases; this is not a claim of measured global TPS improvement.

</details>

Optional localhost smoke tests:

```sh
npm ci --prefix tools/client
python3 tools/smoke_target.py --target 1.21.11 --accept-eula --client-probes --java /path/to/jdk-21/bin/java
```

Pass `--accept-eula` only after accepting the [Minecraft EULA](https://www.minecraft.net/eula). These tests use temporary localhost servers, not a production server.

## Roadmap

- [x] Integrate the prediction engine and Sentinel policies into one plugin.
- [x] Unify commands, permissions, and active branding.
- [x] Add bounded diagnostics, webhook handling, and automated release verification.
- [x] Publish the first source-buildable server target.
- [x] Add independent build profiles and a gated server-test matrix for the published 1.21.x and 26.x targets.
- [ ] Extend coverage as further Paper versions become available.
- [ ] Expand client-version and gameplay compatibility testing.
- [x] Add packet-level combat checks (autoclicker, aura, auto-totem, crystal aura) and enforcing default punishments.
- [ ] Profile sustained load and test against a broader controlled cheat-client suite.

## Releases

Each release contains `SentinelAC-<version>-paper.jar`, `SHA256SUMS.txt` and `VERIFICATION.json` (the build and per-version test report produced by CI). Check the JAR with `sha256sum -c SHA256SUMS.txt`.

## Credits & license

**SentinelAC — created and maintained by azicoo.**

The integrated engine is derived from **[GrimAC](https://github.com/GrimAnticheat/Grim)**, with APIs from **[GrimAPI](https://github.com/GrimAnticheat/GrimAPI)**. Thank you to **DefineOutside and all upstream contributors**, and to the maintainers of the third-party libraries used by this project.

Distributed under **[GPL-3.0](LICENSE)**. Original copyright and license notices remain in the source submodules and the JAR's `META-INF/NOTICE.txt`, `META-INF/LICENSE.txt`, and `META-INF/licenses/` entries.
