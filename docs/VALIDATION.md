# SentinelAC 2.6.0 validation matrix

Each Paper version gets an independently compiled, labelled and tested JAR. Publication is gated on success of the entire matrix; a failed target prevents the release job from running.

## Pinned server targets

| Paper version | Artifact | Java | Paper build/channel |
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

The download URLs and SHA256 values are pinned in `tools/server-matrix.json`. Requested names without published Paper builds, 1.21.2 and 26.1, are listed there as unavailable. The 26.1 line is tested using its published 26.1.1/26.1.2 server versions. ALPHA labels above belong to Paper and remain disclosed.

## Per-target checks

- Compile against the target's Paper API, using Java 21 for 1.21.x and Java 25 for 26.x.
- Run all 77 unit/regression tests, including the embedded target guard test.
- Verify exact filename, plugin version, API version and target metadata; verify core bytecode and that no bundled class requires a newer JVM than the target.
- Check engine/API classes, all backend templates, codec metadata, legal notices, duplicate entries and active branding.
- Start the exact hash-pinned Paper build on an isolated offline localhost world, first alone and then with ViaVersion/ViaBackwards 5.12.2.
- Check readiness, all root command aliases/help, valid reload, invalid-policy rejection and recovery, SQLite, local dump and prediction timing.
- Resolve the server's native wire-protocol number. Where the pinned client library has an exact codec match, exercise native clean login/idle, blacklist-channel kick and impossible upward movement with a Simulation audit finding and correction. Patch versions sharing a protocol may use the same codec; the resolved codec is recorded.
- Exercise translated 1.21.11 and 26.1 clients on every Via server, including the blacklist probes, and run the movement probe through the 26.1 client.
- Confirm orderly shutdown and save both server logs.

The client library is pinned to minecraft-protocol 1.68.0. Its newest codec is 26.1. Native modern gameplay is not claimed when no exact protocol match exists; native server startup/commands and translated probes still run. Test reports distinguish those cases.

## Tests and output

There are 77 tests per target (1,155 across a successful 15-target run). These cover pinned upstream datastore concurrency/ownership/recovery and verbose schemas, plus Sentinel policies, queues, permissions, configuration, ray arithmetic and exact-target packaging. They are not a comprehensive cheat-client corpus.

The changed ray arithmetic is compared with the original over 25,000 random cases plus boundary cases. No global TPS improvement is inferred from that comparison. Webhook behavior uses mocks; external Discord delivery and external MySQL/PostgreSQL/MongoDB/Redis integration are not certified by these tests.

CI uploads `verification-paper<VERSION>.json`, the target JAR, unit-test output and native/Via smoke logs. The release aggregates the reports as `VERIFICATION.json` and includes `SHA256SUMS.txt`. These reports identify the actual checked artifact and coverage; consult them together with the successful GitHub Actions run.

## Remaining limits

Full gameplay sessions, vehicles/elytra/PvP, unusual physics changes from other plugins, high ping/low TPS and sustained high-online profiling require staging tests. ViaBackwards vehicle limitations remain. Channel blacklisting observes advertised channels and cannot enumerate every hidden mod. Rate policies are server rules rather than standalone proof of cheating; the optional kick profile needs calibration.
