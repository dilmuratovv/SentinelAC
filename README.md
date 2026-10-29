<div align="center">

# SentinelAC

### Fair play starts with a stronger server.

A Minecraft anticheat project by **azicoo**, built around server-side prediction, configurable enforcement, and practical tools for server administrators.

[![Build](https://github.com/dilmuratovv/SentinelAC/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/dilmuratovv/SentinelAC/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/dilmuratovv/SentinelAC?color=ec4899&label=release)](https://github.com/dilmuratovv/SentinelAC/releases)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
[![Author](https://img.shields.io/badge/author-azicoo-8b5cf6.svg)](https://github.com/dilmuratovv/SentinelAC)

[Download](https://github.com/dilmuratovv/SentinelAC/releases) · [Installation](#installation) · [Commands](#commands) · [Compatibility](#compatibility) · [Validation](docs/VALIDATION.md)

</div>

---

## About

SentinelAC combines a prediction-based anticheat engine with server policy controls and bounded diagnostics. **Each Paper server version has its own standalone JAR**, with its exact target checked at startup. Movement, combat, packet behavior, and server rules are managed through a shared lifecycle and the `/sac` command tree.

Version-specific builds cover the published Paper targets in the compatibility table below. Choose the file matching your server version; the engine is included in each build.

**Created and maintained by azicoo.**

## Features

| Area | What SentinelAC provides |
|---|---|
| **Movement prediction** | Server-side movement simulation, with checks for impossible movement, timing, and velocity behavior. |
| **Combat & packets** | Reach, hitbox, knockback, packet-order, and malformed-packet checks from the integrated engine. |
| **Channel blacklist** | Configurable exact-channel and namespace rules, checked on join and later channel registrations, with optional kicks. |
| **Action limits** | Sliding-window crystal and respawn-anchor policies, including a shared crystal budget for both hands. |
| **Staff tools** | Alerts, profiles, recent findings, persistent history, prediction timing, and configuration reloads. |
| **Bounded diagnostics** | Limited queues and in-memory history, rotating disk logs, and local debug dumps. |
| **Optional Discord alerts** | Administrator-configured webhooks with a bounded queue, finite request lifetime, and limited rate-limit retries. |
| **Source transparency** | Pinned engine/API sources, a documented build process, automated tests, and artifact verification. |

## Installation

1. Download the appropriate JAR from [Releases](https://github.com/dilmuratovv/SentinelAC/releases), checking the supported server and Java versions.
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

Release **2.6.0** provides separate builds for these exact targets. Each goes through its own API compilation, unit tests, artifact inspection and native/Via server probes before publication.

| Paper version | JAR filename | Java | Paper build/channel |
|---|---|---|---|
| 1.21 | `SentinelAC-2.6.0-paper1.21.jar` | 21 | 130 (STABLE) |
| 1.21.1 | `SentinelAC-2.6.0-paper1.21.1.jar` | 21 | 133 (STABLE) |
| 1.21.3 | `SentinelAC-2.6.0-paper1.21.3.jar` | 21 | 83 (STABLE) |
| 1.21.4 | `SentinelAC-2.6.0-paper1.21.4.jar` | 21 | 232 (STABLE) |
| 1.21.5 | `SentinelAC-2.6.0-paper1.21.5.jar` | 21 | 114 (ALPHA) |
| 1.21.6 | `SentinelAC-2.6.0-paper1.21.6.jar` | 21 | 48 (STABLE) |
| 1.21.7 | `SentinelAC-2.6.0-paper1.21.7.jar` | 21 | 32 (STABLE) |
| 1.21.8 | `SentinelAC-2.6.0-paper1.21.8.jar` | 21 | 60 (STABLE) |
| 1.21.9 | `SentinelAC-2.6.0-paper1.21.9.jar` | 21 | 59 (ALPHA) |
| 1.21.10 | `SentinelAC-2.6.0-paper1.21.10.jar` | 21 | 130 (STABLE) |
| 1.21.11 | `SentinelAC-2.6.0-paper1.21.11.jar` | 21 | 132 (STABLE) |
| 26.1.1 | `SentinelAC-2.6.0-paper26.1.1.jar` | 25 | 29 (ALPHA) |
| 26.1.2 | `SentinelAC-2.6.0-paper26.1.2.jar` | 25 | 74 (STABLE) |
| 26.2 | `SentinelAC-2.6.0-paper26.2.jar` | 25 | 135 (STABLE) |
| 26.3 | `SentinelAC-2.6.0-paper26.3.jar` | 25 | 230 (STABLE) |

**Unavailable names:** Paper has no published 1.21.2 or exactly 26.1 server build. The 26.1 line is covered through its published 26.1.1 and 26.1.2 versions. ALPHA labels above describe the official Paper builds and are not a claim of Paper stability.

ViaVersion/ViaBackwards 5.12.2 are used in the translation tests. Native wire-protocol tests run where a matching client codec is available; full gameplay compatibility is not inferred from login and enforcement probes. Other server versions and Folia are outside this matrix.

See [Validation](docs/VALIDATION.md) and the release's `VERIFICATION.json` for exact coverage and staging limits.

## Configuration & enforcement

- **Channel rules:** Match exact identifiers or namespaces. A matching advertised channel can trigger a kick. Clients can hide channels, so this feature is not a complete inventory of installed mods.
- **Action limits:** Excess crystal or anchor actions are cancelled according to server policy. Exceeding a rate limit alone is not proof of cheating.
- **Punishments:** An [optional kick-oriented profile](deployment/SentinelAC/punishments.yml) is provided for staging and calibration. It is not installed automatically and does not apply permanent bans.
- **Reloads:** Invalid Sentinel policy settings are rejected before application. Diagnostic buffer/log-size changes require a restart; inspect the console if an engine subsystem fails to reload.
- **Diagnostics:** Logs rotate by size and count. Debug dumps remain on the server. Webhooks are disabled by default, and no bStats or upstream update/paste requests are started by SentinelAC.

Test new builds and punishment thresholds on a staging server with your plugin set. Detection coverage and resource use depend on gameplay, network conditions, and configuration; zero lag, zero false positives, and universal cheat detection are not guaranteed.

## Build from source

Use **Maven 3.9+**, **Python 3**, and **Git**. Select **JDK 21** for a 1.21.x profile or **JDK 25** for a 26.x profile. Each profile pins the matching Paper API and target metadata.

```sh
git clone --recurse-submodules https://github.com/dilmuratovv/SentinelAC.git
cd SentinelAC
git submodule update --init --recursive
mvn -B -ntp -Ppaper-1.21.11 clean verify
python3 tools/verify_artifact.py plugin/target/SentinelAC-2.6.0-paper1.21.11.jar
```

Replace `paper-1.21.11` with the required profile, such as `paper-26.3`. Output: `plugin/target/SentinelAC-2.6.0-paper<VERSION>.jar`. The default profile targets Paper 26.2. Never install multiple Sentinel builds on the same server.

<details>
<summary><strong>Build architecture</strong></summary>

- `upstream/engine` and `upstream/api` preserve the pinned original sources.
- `tools/generate_engine.py` produces the Sentinel source port and explicit modifications during the build.
- `plugin/` compiles the engine, APIs, compatibility shim, and Sentinel modules from source.
- `runtime/` extracts third-party libraries and protocol assets from a hash-verified distribution. Engine/API bytecode and the source-built shim are excluded from that carrier.
- Artifact verification checks required resources, codec metadata, core bytecode, duplicate entries, legal notices, and active branding.

The release pipeline requires **77 automated tests per target**, artifact verification, and localhost server/protocol probes for every asset before publication. The ray arithmetic change is checked against the original over 25,000 random cases and boundary cases; this is not a claim of measured global TPS improvement.

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
- [ ] Profile sustained load and test against a broader controlled cheat-client suite.

## Credits & license

**SentinelAC — created and maintained by azicoo.**

The integrated engine is derived from **[GrimAC](https://github.com/GrimAnticheat/Grim)**, with APIs from **[GrimAPI](https://github.com/GrimAnticheat/GrimAPI)**. Thank you to **DefineOutside and all upstream contributors**, and to the maintainers of the third-party libraries used by this project.

Distributed under **[GPL-3.0](LICENSE)**. Original copyright and license notices remain in the source submodules and the JAR's `META-INF/NOTICE.txt`, `META-INF/LICENSE.txt`, and `META-INF/licenses/` entries.
