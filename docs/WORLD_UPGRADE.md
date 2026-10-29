# World compatibility validation (release gate)

No cross-version world upgrade has been verified for this continuation. A build
passing is not evidence of world compatibility. Never test on your only world.
Minecraft downgrades are unsupported. Stop if Minecraft rejects an upgrade path;
do not edit DataVersion to force it.

1. Stop the source server cleanly. Record exact Minecraft, Forge, Regrowth, mods,
   datapacks and configuration versions. Back up the whole server/world, not only
   `region/`. Hash the backup and keep it read-only outside the test directory.
2. Create a disposable complete copy. First upgrade to vanilla-supported target
   versions with an otherwise compatible mod set. The original 1.21.6 Forge mod
   cannot be loaded into a NeoForge 26.x process. Do not mix incompatible loaders.
3. Install the matching Regrowth target JAR and external KotlinLangForge. Copy
   `config/regrowth-common.toml`. Start with villageChanges=false; review the
   effective settings and warnings before enabling terrain maintenance.
4. Record structures, bells/control blocks, villagers/professions, paths, fences,
   farms, block entities, inventories, player builds, modded blocks, entity counts,
   advancements and datapack IDs before/after. Check logs for missing registries,
   config corrections, unknown datapacks, and serialization errors.
5. In a disposable pen exercise each configured animal and villager behavior.
   Mark protected test blocks and verify neither block nor adjacent block entities
   change. Exercise unloaded chunk boundaries and multiple dimensions.
6. Stop cleanly, restart, compare snapshots. Repeat at least three save/load cycles.
   Confirm original backup hashes are unchanged.
7. Repeat with independent copies for 26.1.2 -> 26.2 and 26.2 -> 26.3 only if the
   actual Minecraft releases support that upgrade. Record exact versions/results.

Original Regrowth registers no blocks, items or SavedData type. Its durable world
changes are vanilla terrain and advancements; its village control is the block
over a bell. Keep the `regrowth` namespace and advancement paths. Do not delete
existing world `data/` files from other continuations: the Fabric reference adds
its own village_controls format, which this implementation does not migrate or
claim to interpret. Adaptive profiles and overrides are not enabled.

Config keys containing trailing spaces are intentional legacy identifiers.
NeoForge config correction must be tested on copies; no blanket rewrite, rename,
or deletion of the user's config is permitted. Syntactically malformed TOML is
preserved for diagnosis instead of silently replacing it with defaults.
