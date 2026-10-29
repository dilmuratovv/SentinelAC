# Paper 26.2 validation

Date: 2026-10-29. Release candidate: **SentinelAC 2.4.0-paper26.2**.

## What was verified locally

| Check | Result | Scope |
|---|---|---|
| Maven build on JDK 25 | PASS | Compiles against pinned Paper API 26.2 build 135 and official Grim Bukkit API |
| JUnit tests | PASS, 23 tests, zero failures/errors/skips | Channel boundaries, locale, config rejection, legacy conversion, sliding-window bursts, permission gates, exemption, deferred kicks, action budgets, bounded history, log sanitization, public API readiness/cancellation |
| Artifact inspection | PASS | Java 25 non-preview classes; Paper 26.2 manifest; required Grim dependency; no bundled engine/server classes or stale config templates |
| Native Paper startup | PASS | Paper 26.2 build 135 + official GrimAC 2.3.74 + Sentinel |
| Native console commands | PASS | info, successful reload, invalid reload rejection, recovery reload, perf, clean stop |
| Via startup | PASS | Above plus ViaVersion 5.12.2 / ViaBackwards 5.12.2 on the backend |
| 1.21.11 translated client | PASS | Offline localhost clean login/idle; a second client declaring `baritone:channel` was kicked |
| 26.1 translated client | PASS | Offline localhost clean login/idle; a second client declaring `baritone:channel` was kicked |

The channel probes exercise a declared-channel policy. They do not demonstrate detection of an installed/hidden Baritone mod. The diagnostic logs for these probes contained the blocked-channel findings and no findings for the two clean clients during their short login/idle sessions.

Upstream artifact URLs, version IDs and SHA-256/SHA-512 values are pinned in `tools/test-dependencies.json`. The final JAR can be rebuilt with `mvn -B clean verify`; `tools/verify_artifact.py` validates its packaging. CI builds and uploads the companion JAR and test summaries. Optional workflow smoke tests require an explicit EULA checkbox.

## Important remaining limits

- No real native Minecraft 26.2 game client was driven by these tests. Native server/plugin startup was tested. The protocol test library supports 1.21.11 and 26.1, used through ViaBackwards.
- These are boot, command, API, policy and limited protocol tests. Full survival/PvP/crystal/elytra/vehicle gameplay, a cheat corpus, prolonged low-TPS/high-ping play, and CPU/RAM load benchmarks were not performed.
- **Upstream GrimAC warns about ViaBackwards on 1.21.2+ servers and older clients using vehicles.** This warning is not suppressed or presented as fixed. Neither the tested handshakes nor a Paper version label certify every translated physics combination. See the [upstream initialization check](https://github.com/GrimAnticheat/Grim/blob/v2.3.74/common/src/main/java/ac/grim/grimac/manager/init/start/ViaVersion.java).
- The localhost test servers were offline, bound to 127.0.0.1. Online authentication was not tested. The execution environment could not resolve the Mojang public-key endpoint and emitted environment/system-information warnings; these were not classified as Sentinel test failures.
- The optional enforcement profile is a configurable starting point, not a measured universal ban threshold. It uses kicks for repeated flags and no permanent bans. Review false positives on a server copy before applying it.
- The public Grim API and physics engine remain external dependencies. This change does not patch upstream Discord retry/queue behavior. Grim's webhook is off in the tested default configuration; Sentinel has no outgoing webhook queue.

## Manual gameplay checklist before a public rollout

1. Use a backup/server copy with the intended Via placement and all gameplay plugins.
2. Test native 26.2 and each client version you plan to allow: movement, water, elytra, knockback, combat, inventory, crystals, anchors and vehicles.
3. Repeat with realistic latency and TPS variation. Inspect Grim and Sentinel findings and `dropped` diagnostics.
4. Verify staff permissions, exemptions, blacklist kick messages, valid/invalid reloads, restart and log rotation.
5. Decide the allowed client range. Avoid promising support for translated vehicle combinations upstream does not support.
6. Calibrate the optional kick profile from observed behavior; retain logs for reviewing disputed actions.

Source ownership is preserved in Git history. The old opaque modified-engine binaries are intentionally removed from the current source tree. Install the official Bukkit Grim release alongside the new companion, following the migration instructions in the README.
