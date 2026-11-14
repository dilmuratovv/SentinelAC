# SentinelAC 2.7.0 validation matrix

One JAR is compiled once and then verified on every supported Paper version. Publication is gated on success of the entire matrix; a failed target prevents the release job from running.

## Pinned server targets

| Paper version | Java | Paper build/channel |
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

The download URLs and SHA256 values are pinned in `tools/server-matrix.json`. Requested names without published Paper builds, 1.21.2 and 26.1, are listed there as unavailable.

## Checks

Build (once, Java 21):
- Compile against the Paper 1.21 API and run all 77 unit/regression tests, including the supported-version guard test, which compares the JAR metadata with `tools/server-matrix.json`.
- Verify filename, plugin version, API version and the supported-version list; verify core bytecode and that no bundled class requires a newer JVM than Java 21.
- Check engine/API classes, all backend templates, codec metadata, legal notices, duplicate entries and active branding.

On every supported Paper version, with the same JAR (Java 21 for 1.21.x, Java 25 for 26.x):
- API link check (`tools/api_compat.py`): every platform class, method and field the JAR links against must resolve on that version when it resolves on Paper 1.21. The prebuilt PacketEvents libraries bundled in the JAR carry version-guarded code for API that 1.21 lacks; those references are unresolvable on the baseline too and are reported only as a count.
- Start the exact hash-pinned Paper build on an isolated offline localhost world, first alone and then with ViaVersion/ViaBackwards 5.12.2.
- Check readiness, all root command aliases/help, valid reload, invalid-policy rejection and recovery, SQLite, local dump and prediction timing.
- Resolve the server's native wire-protocol number. Where the pinned client library has an exact codec match, exercise native clean login/idle, blacklist-channel kick and impossible upward movement with the real client.
- Exercise translated 1.21.11 and 26.1 clients on every Via server, including the blacklist probes, and run the movement probe through the 26.1 client.
- Confirm orderly shutdown and save both server logs.

The client library is pinned to minecraft-protocol 1.68.0. Its newest codec is 26.1. Native modern gameplay is not claimed when no exact protocol match exists; native server startup/commands and translated-client probes still run on every target.

## Tests and output

There are 77 tests, run once in the build; every target report carries the same result. These cover pinned upstream datastore concurrency/ownership/recovery and verbose schemas, plus Sentinel policies, queues, permissions, configuration, ray arithmetic and exact-target packaging. They are not a comprehensive cheat-client corpus.

The changed ray arithmetic is compared with the original over 25,000 random cases plus boundary cases. No global TPS improvement is inferred from that comparison. Webhook behavior uses mocks; external Discord delivery and external MySQL/PostgreSQL/MongoDB/Redis integration are not certified by these tests.

CI uploads `verification-paper<VERSION>.json` per target, the JAR, unit-test output and native/Via smoke logs. The release aggregates the reports as `VERIFICATION.json` and includes `SHA256SUMS.txt`. These reports identify the actual checked artifact and coverage; consult them together with the successful GitHub Actions run.

## Remaining limits

Full gameplay sessions, vehicles/elytra/PvP, unusual physics changes from other plugins, high ping/low TPS and sustained high-online profiling require staging tests. ViaBackwards vehicle limitations remain. Channel blacklisting observes advertised channels and cannot enumerate every hidden mod. Rate policies are server rules rather than standalone proof of cheating; the optional kick profile needs calibration.
