# SentinelAC 2.6.0 — Separate Paper Builds

Created and maintained by **azicoo**.

This release provides a **separate JAR for each exact Paper server version**. Choose the matching file below. The target is embedded in each JAR and checked at startup.

| Paper version | Download filename | Java | Tested Paper build/channel |
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

Paper does not publish builds named **1.21.2** or exactly **26.1**. The 26.1 server line is available as **26.1.1** and **26.1.2**. No untested placeholder JAR is provided for an unavailable server version. Paper's published 1.21.5, 1.21.9 and 26.1.1 builds retain an ALPHA channel label; testing here does not relabel those Paper builds as stable.

## Installation

Back up the server, configuration and history databases. Replace the previous Sentinel JAR with the single file matching your server version; remove separately installed GrimAC engine JARs. Restart and run `/sac info`. Do not install multiple Sentinel JARs together.

Commands: `/sac`, `/sentinel`, `/sentinelac`. Configuration: `plugins/SentinelAC/`.

## Verification gate

Publication requires every target to pass its own source build, 77 automated tests, artifact inspection, native server boot and commands/reload/dump, and ViaVersion/ViaBackwards 5.12.2 server probes. Probes exercise clean login/idle, blacklist-channel kicks and a core Simulation finding with server correction. Native game-protocol probes run when the pinned test library has an exact wire-protocol match; translated probes also run on every target.

`SHA256SUMS.txt` covers all 15 JARs. `VERIFICATION.json` records each server build, Java version, artifact hash, unit-test results and the exact native protocol coverage. The release stays a draft until every JAR and both verification files have uploaded successfully.

## Scope

Start with a staging server. These probes do not replace full gameplay, vehicle, PvP, high-ping or sustained-load testing. Some native modern client protocols are unavailable in the test library and are reported explicitly. ViaBackwards vehicle limitations remain. Hidden client mods cannot all be discovered from advertised channels. No zero-lag, zero-false-positive or universal-detection guarantee is made.

The prediction engine and APIs are compiled from pinned sources. Original copyright and GPL notices remain intact. Thanks to GrimAC, GrimAPI, DefineOutside and all upstream contributors.

Source, including submodules:
```sh
git clone --branch v2.6.0 --recurse-submodules https://github.com/dilmuratovv/SentinelAC.git
```
