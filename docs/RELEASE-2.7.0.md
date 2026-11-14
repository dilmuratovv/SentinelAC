# SentinelAC 2.7.0 — One JAR for Paper 1.21 to 26.3

Created and maintained by **azicoo**.

Previously every Paper version had its own JAR. This release ships **one file, `SentinelAC-2.7.0-paper.jar`**, that works on every supported Paper version. There is nothing to choose: download it, put it in `plugins/` and restart. The server version is checked at startup.

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

The JAR is Java 21 bytecode, so it runs on Java 21 (Paper 1.21.x) and Java 25 (Paper 26.x). Paper has no published 1.21.2 or exactly 26.1 server build, and those, older versions and newer versions are refused at startup with a clear message. Paper's published 1.21.5, 1.21.9 and 26.1.1 builds retain an ALPHA channel label; testing here does not relabel those Paper builds as stable.

## What changed

- One JAR replaces the 15 per-version JARs. File name: `SentinelAC-2.7.0-paper.jar`, plugin version `2.7.0`.
- The build is compiled once against the oldest supported Paper API (1.21). CI links it against every other supported version (`tools/api_compat.py`) and boots that same JAR on each one.
- `/sac version` and the startup log show the plain plugin version, without a `-paper<version>` suffix.
- Nothing changes in detection, configuration or commands compared with 2.6.0.

## Installation

Back up the server, configuration and history databases. Replace the previous Sentinel JAR with this single file; remove separately installed GrimAC engine JARs. Restart and run `/sac info`. Do not install multiple Sentinel JARs together.

Commands: `/sac`, `/sentinel`, `/sentinelac`. Configuration: `plugins/SentinelAC/`.

## Verification gate

Publication requires the single JAR to pass 77 automated tests and artifact inspection once, and then, on every one of the 15 Paper versions, an API link check, a native server boot with commands/reload/dump, and ViaVersion/ViaBackwards 5.12.2 server probes. Probes exercise clean login, blacklist-channel kicks and movement where the test client supports the protocol.

`SHA256SUMS.txt` covers the JAR. `VERIFICATION.json` records, for every server build, the Java version, the same JAR hash, unit-test results and the native protocol coverage. The release stays a draft until the JAR and both files are uploaded.

## Scope

Start with a staging server. These probes do not replace full gameplay, vehicle, PvP, high-ping or sustained-load testing. Some native modern client protocols are unavailable in the test library and are reported explicitly in `VERIFICATION.json`.

The prediction engine and APIs are compiled from pinned sources. Original copyright and GPL notices remain intact. Thanks to GrimAC, GrimAPI, DefineOutside and all upstream contributors.

Source, including submodules:
```sh
git clone --branch v2.7.0 --recurse-submodules https://github.com/dilmuratovv/SentinelAC.git
```
