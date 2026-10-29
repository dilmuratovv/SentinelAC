# SentinelAC 2.5.0 — validation

Test date: **2026-10-29**. Target: **Paper 26.2 build 135**, **Java 25**. This report describes the standalone source build, with no separate anticheat engine plugin installed.

## Build and artifact

- `mvn clean verify`: **76 tests passed; 0 failures, errors or skips**, across 19 suites. This includes 48 pinned upstream regression tests and 28 Sentinel tests.
- All 949 engine/API source files are generated from pinned source submodules and compiled, alongside Sentinel sources. The final archive has 1,334 non-library core classes, including compiler-generated nested classes.
- SHA256: `744a4d5d19722f228be9a90ac18f9bd626f0c98fbffd10100fc2071aef29bcfb`.
- `tools/verify_artifact.py` passes: standalone loader, bundled PacketEvents, Java 25 core bytecode, no old engine/server classes, no duplicate archive entries, all five backend defaults, codec metadata and original legal notices.
- Classfile UTF8 constants, resource contents and archive paths have no old branding outside legal notices. The eight FastUtil serialized-lambda descriptor strings missed by ordinary Shade relocation are corrected, with classfile lengths preserved.
- The third-party carrier's SLF4J copyright/license is retained separately from the plugin GPL license. The upstream compatibility shim is excluded from the carrier and compiled from source.

Source pins and library carrier checksum are documented in README and `tools/test-dependencies.json`. Raw upstream sources and their licenses remain in submodules.

## Automated regression coverage

Sentinel tests cover immutable exact-channel/namespace matching, invalid configuration rejection, monotonic sliding windows and burst boundaries, shared offhand/main-hand crystal limits, respawn-anchor charging distinction, delayed kicks with policy/exemption rechecks, finite recent history, log sanitation, permission checks, and preserving cancelled public API flags.

The changed ray arithmetic is compared bit-for-bit with the pinned original over **25,000 random rays/boxes** and signed-zero, parallel, inside-origin and boundary cases. This verifies the scoped arithmetic change; it does not establish an improvement in global TPS. The JVM may already eliminate some original allocations.

Webhook tests exercise queue overflow, network failures, missing rate-limit headers, the three-attempt limit, one in-flight request, expiry and shutdown completion. No external webhook was sent. Existing upstream tests cover session continuity/publication, lease ownership, heartbeat scheduling, reconnect/recovery and verbose schemas.

## Live localhost Paper tests

The smoke harness ran temporary **offline-mode, localhost-only** flat worlds. Test JARs were hash verified. Native case: only SentinelAC. Via case: SentinelAC, ViaVersion **5.12.2**, ViaBackwards **5.12.2**.

Both cases passed:

- Startup and `Sentinel ready: Paper 26.2`; no engine initialization/command registration failure.
- `/sac info`, `/sentinelac version`, `/sentinel help`, and the custom help entries.
- Valid reload, rejection of crystal limit `0`, then recovery with valid configuration.
- Local `/sac dump` file creation and `/sac perf` output.
- SQLite initialization/reload and orderly shutdown.

Via client probes passed:

| Client | Probe | Result |
|---|---|---|
| 1.21.11 | Login and 2.5 seconds idle | Stayed connected |
| 1.21.11 | Register `baritone:channel` | Kicked with the matching rule |
| 26.1 | Login and 2.5 seconds idle | Stayed connected |
| 26.1 | Register `baritone:channel` | Kicked with the matching rule |
| 26.1 | Impossible upward movement | Server correction and an actual core `Simulation` audit finding |

The movement probe answers transaction pings, acknowledges teleports and sends movement/tick-end packets. The harness requires a `Simulation` finding as well as a correction, since a vanilla teleport alone would not prove the engine acted.

Logs are produced at `target/smoke/native/smoke-output.log` and `target/smoke/via/smoke-output.log`. Test-environment network warnings from Mojang public-key requests and optional server/plugin update requests were observed; online authentication was not tested.

## Limits and staging work

`minecraft-protocol` 1.68.0 provides 26.1 and 1.21.11, but no native 26.2 game client. Native 26.2 **server boot/commands** were tested, not a complete native player session. The probes do not simulate normal gameplay physics comprehensively or certify zero false positives.

Upstream's warning about ViaBackwards vehicle support on 1.21.2+ servers remains. Older client versions, native 26.2 gameplay, vehicles, elytra, knockback/PvP, low TPS/high ping and interactions with the actual server plugin set need staging playtests. High-online CPU/heap profiling and controlled cheat-client tests have not been performed; no zero-lag or universal detection guarantee is made.

Only SQLite was exercised live. MySQL/PostgreSQL/MongoDB/Redis templates are present, but those external backends and their optional driver plugins were not integration tested. Discord delivery was tested with mocks only. The optional kick punishment profile is not a universally calibrated enforcement policy and is not installed automatically.

Channel blacklisting observes advertised plugin channels; clients can hide them. It cannot enumerate every installed mod or detect every client-only ESP/X-ray. Existing history databases/configurations from old modified binaries are not automatically certified for migration. Back up and compare them before adoption.
