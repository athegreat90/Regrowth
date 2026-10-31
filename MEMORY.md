# Regrowth continuation decisions

Read this file before changes. Every independent task starts with a unique branch
from current main and ends in a PR targeting main. Inspect status/remotes/default
branch/open PRs first; preserve unrelated work. Never commit/push directly to main
or master, force-push them, or auto-merge. This task uses feat/kotlin-neoforge-port.

Mission: Kotlin-first, NeoForge-only Regrowth with external KotlinLangForge and
separate targets for Minecraft 26.1.2, 26.2, 26.3. These are intended targets, not
claims of verified support. Completion is blocked until all release gates pass.

## Audited sources (2026-10-29)

- https://github.com/mactso/Regrowth — 8bda8b5ff4b9e456f16384093ea0f0580716b598
- https://github.com/cairodavila/regrowth-revamped — a9e96c4237a448291d27f23385df12140c1d5cad
- https://github.com/btwonion/KotlinLangForge — 6e562c10f8ee019f91781e0936d638e80b2e2b81
- https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle — f31f90e71a3e8fca9d8b821cb25fb5a767d3a2d6

## ADR-001 — 2026-10-29: toolchain and module layout

Context: three targets and mandatory KotlinLangForge. Alternatives: duplicated
projects, universal JAR, common Minecraft abstraction. Selected: pure common Kotlin
rules/tests plus three thin ModDevGradle modules sharing gameplay sources. Rationale:
separate classpaths/JARs catch API drift without inventing a world abstraction.
Java 25, Gradle 9.2.1, ModDevGradle 2.0.148, Kotlin 2.4.21; KLF external artifact
`dev.nyon:KotlinLangForge:2.16.0-k2.4.21-3.1+neoforge` verified in Maven metadata.
NeoForge pins: 26.1.2.125, 26.2.0.93, 26.3.0.99 verified published metadata.
Consequence: all three must pass independently. Loader is `klf`, generation 3.1;
`klf` is not assumed to be a normal mod dependency ID. Standard Kotlin @Mod class
uses supported IEventBus/ModContainer injection. Runtime/provider tests pending.
Relevant commit/PR: this branch; final evidence below.

## ADR-002 — 2026-10-29: preservation and terrain safety

Context: original owns no SavedData or custom block/item registry. Alternatives:
blanket config rewrite/reference adaptive data migration. Selected: preserve mod ID,
legacy TOML keys/defaults and resource paths; no adaptive saved-data migration.
Rationale: original terrain uses vanilla blocks, village control uses bell-adjacent
block; unknown reference data must stay untouched. Original duplicate mushroom key
is documented rather than silently renamed. Consequence: no claim of world upgrade
compatibility without disposable fixtures and restart tests. See WORLD_UPGRADE.md.
Early server/player/parity checks, local entity state, loaded-chunk-only reads,
block entity/protected-block exclusion; no off-thread mutations. Fixed-square wall
geometry needs no cache; a bounded 4096-entry entity-type rule/miss cache is invalidated by immutable config publication and cleared on the next server tick or
shutdown and retains no world references. Giant generation uses a loaded clear-volume preflight; legacy overcrowding is
opt-in/off by default. Terrain operations use stricter eligibility and bell-control
creation is manual (FEATURE_AUDIT.md). Back up worlds
and disable villageChanges while reviewing an existing world's settings.
Verification: unit and integration results recorded after execution.

## ADR-003 — 2026-10-29: distribution and releases

Context: Regrowth MIT and KotlinLangForge GPL-3.0-only. Selected: retain MIT notices,
external KLF dependency, no shaded Kotlin runtimes. Combined-distribution legality
is not inferred; see THIRD_PARTY_NOTICES.md. Reference code is evaluated selectively;
Fabric runtime/mixins are prohibited. Development prerelease per main commit must
be gated on all targets, immutable full-SHA tag, three jars and SHA-256 checksums;
write access only in publisher. Never publish to upstream project pages or merge.
Consequence: incomplete validation prevents a release; documentation pushes still
trigger the same required gates. Stable releases require a separate intentional flow.

## Verification and remaining work

See docs/FEATURE_AUDIT.md for current implementation vs actual parity, and
WORLD_UPGRADE.md for the release-blocking fixture procedure. No previous world
fixtures were supplied. No performance results or runtime support are inferred
from compilation. Final command outcomes will be recorded before the PR.

## ADR-004 — 2026-10-29: config reload ownership and observed verification

Context: config reload events may run on a file-watcher thread. Alternatives:
mutating the entity-type cache in that callback, synchronizing every tick, or
publishing an immutable snapshot. Selected: volatile immutable map snapshot;
only the game thread invalidates/clears its classification cache on the next tick.
Rationale: no watcher/game-thread IdentityHashMap mutation race and no tick lock.
Consequence: helpers only observe complete published maps; no world access occurs
in the config callback. Shutdown clears type caches but retains process-wide
COMMON/LOCAL configuration, so a subsequent integrated-server start keeps working.
A GameTest explicitly covers clear/reuse without another config loading event.
Bonemeal also preflights protected neighbors in its loaded growth volume, covered
by a GameTest that protects the air above short grass. Verification: final three builds and all 15 GameTests
passed after this change. Relevant commit is recorded below after committing.

Final local checks: 10 common tests, 4 config tests per target, 5 dedicated GameTests
per target, 6 offline publisher tests, 3 artifact validators and actionlint 1.7.12
passed; no test skips. The initial clean matrix also passed. Full results/measurement
scope are in docs/VALIDATION.md. Config backups preserve exact original bytes.

Open gates: original-world upgrade/restart fixtures, complete gameplay parity,
client/modpack interoperability and sustained performance/GC measurements. No
world compatibility or production performance claim is justified by these checks.
No custom original SavedData exists; Fabric-reference saved data stays untouched.
The branch will be submitted as a draft PR, left unmerged. No stable/development
release or environment publication was executed by the agent.

Implementation and local verification commit: `f63c72e940139136d771f5dc8ec4954c572a10a6` on `feat/kotlin-neoforge-port`.
This revision contains the tested code and documentation; the following commit
only records this reference. The PR targets main and must remain unmerged pending
the documented release gates.

## ADR-005 — 2026-10-29: maintained CI action pins

Context: GitHub's first three-target CI run succeeded, but reported deprecated
Node 20/setup-java v4 actions. Alternatives: retain the old revisions or pin current
stable official releases. Selected: immutable current release SHAs for checkout
7.0.1, setup-java 6.1.0, gradle/actions 6.4.1, upload-artifact 7.0.2 and
download-artifact 8.0.2. Rationale: retain supply-chain pinning while using maintained
runtime generations. Consequence: rerun the matrix to validate the action upgrade;
no gameplay or artifact content changes. Initial successful remote run:
https://github.com/athegreat90/Regrowth/actions/runs/40872949585
Relevant code revision: this workflow-only commit after the implementation revision
recorded above. Final remote result remains visible in the PR checks.

## ADR-006 — 2026-10-29: draft release lookup and retry

The first main publisher uploaded all four assets, then failed: GitHub's by-tag
release endpoint returns 404 for drafts, even to the authenticated publisher.
Use the paginated authenticated releases list as the 404-only fallback, and
verify uploaded assets by numeric release ID. Preserve exact-commit checks,
immutable published-byte verification and rejection of auth/service failures.
Offline publisher tests now model drafts as absent from the by-tag endpoint;
all six pass, including first publish, retry and conflicting commit rejection.
The failed run is 40888736758 at main af666b49777f767d32e9b9a3215140c35433408e.

## ADR-007 — 2026-10-29: retain the create response and use IDs throughout

Run 40899979337 failed after draft creation: immediate discovery returned no
matching release. ADR-006 did not eliminate discovery dependence. Create through
the REST API and retain its returned release ID; upload assets, verify SHA-256
API digests and publish through ID-based REST endpoints. No immediate tag/list
lookup or tag-based CLI upload/edit is needed. Existing drafts remain discoverable
for retries; only a draft targeting the exact commit can be modified. Tests model
a new draft absent from both discovery endpoints, partial upload recovery,
corrupt upload rejection, immutable published bytes and API errors. Nine tests
pass locally. Real Actions publication of this revision remains pending merge;
this change does not claim recovery of the failed draft or runtime gameplay fixes.

## ADR-008 — 2026-10-31: minimum NeoForge runtime versions

The shared dependency template used `[${neo}]`, an exact version requirement,
so the 26.3 artifact built against 26.3.0.99 refused to load on 26.3.0.100.
Use `[${neo},)` for all three artifacts, retaining the existing build pins and
exact Minecraft requirements. The artifact validator now checks each target's
required minimum NeoForge range, preventing an exact-pin regression. Accepting
a loader version is not a claim of tested compatibility with all future versions.
Branch: `fix/neoforge-minimum-version-range`. All three builds/artifact validators
and 12 target config tests passed; 10 common tests were up to date. Five GameTests
passed on each original build pin and additionally on NeoForge 26.3.0.100 using
the same classes/metadata built against .99 (byte-checked against the JAR).
Maven range checks reject .98 and accept .99/.100/.101; .101 was not runtime-tested.
See docs/VALIDATION.md for scope. Existing world, client/modpack and performance
release gates remain.
