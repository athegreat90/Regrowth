# Regrowth: Kotlin NeoForge Continuation

An **unofficial, experimental** Kotlin continuation of [Regrowth by MacTso and
Lupicus](https://github.com/mactso/Regrowth). Animals help regrow vegetation and
villagers maintain their surroundings. This project is not maintained or endorsed
by the original developers.

Original mod pages: [Modrinth](https://modrinth.com/mod/regrowth) ·
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/regrowth).
The unrelated Regrowth HQM modpack is not this mod.
[Cairo D'Avila's Fabric continuation](https://github.com/cairodavila/regrowth-revamped)
was audited for comparison; its adaptive village planner and Fabric code are not
included. See [source notices](THIRD_PARTY_NOTICES.md).

## Compatibility

Each target has its own artifact. Never substitute one version's JAR for another.
These are experimental development targets, **not verified production/world-upgrade
support**. See [validation evidence](docs/VALIDATION.md) for exact check outcomes.

| Minecraft | NeoForge build pin / minimum | Java | Artifact prefix |
|---|---|---|---|
| 26.1.2 | 26.1.2.125 | 25 | `regrowth-kotlin-neoforge-26.1.2-` |
| 26.2 | 26.2.0.93 | 25 | `regrowth-kotlin-neoforge-26.2-` |
| 26.3 | 26.3.0.99 | 25 | `regrowth-kotlin-neoforge-26.3-` |

NeoForge dependency metadata accepts the listed minimum and later versions using
`[minimum,)`; for example, the 26.3 JAR declares `[26.3.0.99,)`, allowing
26.3.0.100 through the version check. Each JAR still requires its exact Minecraft
version. Build dependencies stay pinned to the listed minimums. Passing the version
check does not establish runtime compatibility with every future NeoForge version;
see the validation evidence for versions actually tested.

All use Kotlin compiler **2.4.21** and the external dependency
**KotlinLangForge `2.16.0-k2.4.21-3.1+neoforge` by btwonion**.
The provider ID is `klf`, generation 3.1. It is not Kotlin for Forge or Fabric
Language Kotlin. Provider metadata uses `modLoader="klf"`, `loaderVersion="[1,)"`;
this is distinct from ordinary `[[dependencies.regrowth]]` entries.

## Installation

1. Back up the complete world, mods, datapacks and configuration.
2. Install Java 25 and the matching NeoForge version.
3. Install the matching Regrowth JAR and
   [KotlinLangForge](https://modrinth.com/mod/kotlin-lang-forge) in the process's
   `mods` directory. Obtain KLF separately: it is not bundled in Regrowth.
4. A dedicated server running Regrowth needs KLF. A client running Regrowth also
   needs KLF. Do not infer that an unmodified client can join every server/modpack;
   client interoperability has not been tested here.
5. Review `config/regrowth-common.toml` before using an existing world. Start with
   `["Regrowth Safety"].villageChanges=false` if you need to review village edits.

The development server has loaded this external provider successfully; this does
not validate arbitrary third-party modpacks or every other NeoForge version.

## Gameplay and safety

Implemented areas include animal eating/healing and vegetation growth, biome-aware
sapling planting, hostile-mob torch/grass interactions, coral fans, mushrooms,
villager path repairs, lighting, bell-controlled walls, personal fences, farming,
cleric healing and toolsmith repairs. Preservation and intentional safety changes
are tracked feature by feature in [FEATURE_AUDIT.md](docs/FEATURE_AUDIT.md).

World edits run on the server thread. Per-entity positions/states are local;
unsupported entities are classified through a bounded type cache that is invalidated
on config reload and cleared on the next server tick or shutdown. No worlds or entity instances are cached.
Chunk availability is checked before terrain access; unavailable areas are skipped.
Block entities, configured protected blocks and persistent player leaves are
excluded from direct edits. Player ownership of ordinary vanilla dirt/grass cannot
be detected automatically: configure protection and disable village edits where
necessary. Protection/claim-mod interoperability is not certified.

Giant mushroom generation requires an already loaded, clear volume. Village wall
construction requires the configured control block over an existing bell; this
continuation does not automatically add it over bells. Removing that block stops
wall construction. Adaptive village generation/profiles are not enabled.

## Commands

Commands retain operator/game-master permission requirements (level 2 equivalent):

- `/regrowth debugLevel <0|1|2>` changes the in-memory diagnostic setting.
- `/regrowth info` reports debug level, configured rules and the nearest entity
  intersecting the player's view within eight blocks; also works from the console.

Command debug changes are not claimed to persist across restarts.

## Configuration and worlds

Existing TOML sections, serialized mob rows, the `regrowth` mod ID, translation
keys and advancement identifiers are retained. Keys with trailing spaces such as
`"torchLightLevel "` and `"mushroomXDensity "` are intentional legacy names.
The original declares mushroomXDensity twice; this port retains the effective
later default of 6 and does not invent a silent key migration.

Before NeoForge loads/corrects an existing config, Regrowth preserves an exact
content-addressed `.pre-kotlin-<sha256>.bak` copy alongside it. A changed original
gets another backup; an existing matching backup is reused and checked. Invalid
mob rows are diagnosed and skipped without invalidating valid rows. Syntactically
malformed TOML can still prevent startup; retain the backup and correct the file
explicitly instead of deleting it.

`["Regrowth Safety"]` adds `villageChanges`, `protectedBlocks` (registry IDs), and
`legacyOvercrowding`. Legacy overcrowding damage/void teleport is opt-in and off
by default to protect existing farms; this is an intentional default change.
No automatic import of the Fabric continuation's properties/profiles/saved data
is performed. Unknown world data files are not deleted. Original Regrowth has no
custom SavedData, blocks or items: its durable edits use vanilla world state.

**World upgrades are a release gate, not an established guarantee.** Follow the
[disposable-copy upgrade procedure](docs/WORLD_UPGRADE.md), including clean restart
and persistence checks. No original-mod world fixtures were supplied or certified.
Never downgrade a world or edit DataVersion to bypass Minecraft's checks.

## Build and test

Install a JDK 25 and set `JAVA_HOME` to it; no global Gradle install is needed.
The checked-in wrapper pins Gradle 9.2.1 and its official distribution SHA-256.
From the repository root:

```sh
bash gradlew :common:test :mc2612:build :mc262:build :mc263:build
python3 scripts/assert_test_results.py common mc2612 mc262 mc263
python3 scripts/validate_artifact.py mc2612
python3 scripts/validate_artifact.py mc262
python3 scripts/validate_artifact.py mc263
python3 scripts/run_gametests.py mc2612
python3 scripts/run_gametests.py mc262
python3 scripts/run_gametests.py mc263
python3 -m unittest discover -s scripts/tests -v
```

JARs are in each module's `build/libs/`. GameTests use generated disposable worlds,
not user fixtures. The runner requires a fresh XML report containing all five
required tests and preserves the underlying process exit status. The common tests
include a small parity-gate microbenchmark; it is not a server TPS/GC benchmark.

For interactive development use `bash gradlew :mc263:runClient` (display required)
or `:mc263:runServer` (requires your own Minecraft EULA acceptance). Substitute the
other module for another target. Do not accept legal terms on another person's
behalf. Proxy-managed cloud JDK installations must retain the environment's CA
trust and Java proxy settings; never disable certificate verification.

## Contributions and development releases

Read [MEMORY.md](MEMORY.md) and [CONTRIBUTING.md](CONTRIBUTING.md). Every independent
task starts on a new feature branch from current `main` and ends in a PR to `main`.
Never push directly to `main`/`master` or auto-merge.

CI checks every target on PRs to `main` and manual dispatch. Every push to `main`,
including documentation-only changes, invokes the same checks before publishing
one development prerelease tagged `dev-<full-commit-sha>`. All three JARs and
`SHA256SUMS` are attached. Per-commit concurrency and immutable tags make reruns
idempotent; only the publishing job has write permission. Failed required checks
prevent publication. Existing published asset bytes are checked rather than
replaced. These workflows are reviewed code until actually run on GitHub.

No automatic stable releases, upstream Modrinth/CurseForge publication or PR merges
are configured. A separate intentional process is required for stable releases.

## Limitations and license

Full original gameplay parity, original-world upgrades, client rendering/network
interoperability, reference saved-data migration and long-running server memory/
tick-time behavior are not yet certified. Read the audit before deploying.

Regrowth code/resources retain the original [MIT license](LICENSE).
KotlinLangForge is external and GPL-3.0-only; MIT does not relicense it. No Kotlin
runtime is embedded in the Regrowth JAR. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
for distribution/compliance considerations and the limits of that assessment.
