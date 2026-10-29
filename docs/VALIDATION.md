# Validation evidence — 2026-10-29

## Local verification

Java 25.0.5 (Temurin), Gradle 9.2.1, Kotlin 2.4.21, ModDevGradle 2.0.148,
external KotlinLangForge 2.16.0-k2.4.21-3.1+neoforge.

| Minecraft | NeoForge | Build | Config compatibility tests | Dedicated GameTests | Artifact validation |
|---|---|---|---|---|---|
| 26.1.2 | 26.1.2.125 | Pass | 4 passed | 5 passed | Pass |
| 26.2 | 26.2.0.93 | Pass | 4 passed | 5 passed | Pass |
| 26.3 | 26.3.0.99 | Pass | 4 passed | 5 passed | Pass |

- Shared Kotlin unit tests: 10 passed, zero failed/skipped. Includes legacy mob
  parsing/serialization, intervals/parity, bounded-cache behavior, exact config
  backups and a parity-gate microbenchmark.
- Four config tests per target: missing defaults, partial values, malformed values,
  and five historical TOML read/write/correction cycles without changing preferences.
- Five GameTests per target: eating and subsequent entity isolation/chest retention;
  configured protected vegetation and protected bonemeal neighbors; 100 calls at a distant unloaded chunk without
  loading it; rule-cache invalidation, shutdown/reuse without config loss, and sapling spacing; village feature
  flag and floor torch placement. Random event decisions use an injected roll.
- Every target starts a dedicated GameTest server through KLF, loads original
  advancements/resources, completes all five tests, saves dimensions and stops.
  This is a GameTest server smoke test, not a certified normal modpack deployment.
- Artifact checker verifies exact target ranges, mod ID/provider, unchanged JSON
  resources/advancement paths, license notices and absence of embedded Kotlin,
  KLF, Fabric loader/API code or old Forge metadata.
- Six offline prerelease tests passed (including multiple API error subcases):
  first publication/rerun idempotency, immutable asset bytes, conflicting tags,
  resumable drafts, unverified draft identity, and service/auth failures.
- actionlint 1.7.12 passed. Official binary checksum verified before use.
- Gradle wrapper regenerated; official distribution SHA-256 pinned. Clean builds
  passed for all three targets. NeoForm dependency/decompilation caches were reused.
  The final config-publication fix was then rebuilt and all servers rerun.

Commands: see README.md. No zero-test run is accepted by the supplied runners.
GitHub-hosted CI execution/publication is distinct from these local results; no
GitHub development release was created during this task.

## Performance evidence and limits

One local, warmed-up pure parity-filter microbenchmark reported:

```
parityGate iterations=1000000 warmups=5 samples=7 medianNs=8102554 nsPerCall=8.102554 java=25.0.5
```

This is one million calls/sample, five warmups, seven samples and the median;
other builds/processes shared this cloud machine. It measures only the pure
filter, not entity world access, allocations, village planning, tick time, TPS or
long-running memory. No relative improvement or production-load result is claimed.

## Unverified release gates

- Original-mod world fixtures and complete upgrade/restart/persistence comparisons
  for original -> 26.1.2 -> 26.2 -> 26.3 were not available/performed. No original
  fixture was modified. Original has no custom SavedData; no fake SavedData test is
  substituted for an actual world-upgrade test.
- Full per-feature gameplay parity, giant mushroom shape/statistics, wall/fence
  geometry, biome coverage, all professions and third-party protection interactions
  need broader GameTests/manual validation. Safety differences are explicit in
  FEATURE_AUDIT.md and are not advertised as exact original behavior.
- Client startup/rendering/network interoperability was not tested.
- Normal dedicated-server EULA/modpack startup and sustained production-load/GC
  tests were not performed; dedicated GameTest startup did run for all targets.
- Remote workflow execution and actual prerelease publication require GitHub
  Actions; source syntax/offline publisher behavior passed locally.

Known nonfatal diagnostics: deprecated ModConfigSpec list overloads and the
26.3 `logoFile` metadata property. No tests were skipped to hide these diagnostics.
This is experimental work pending the release gates, not a production readiness claim.
