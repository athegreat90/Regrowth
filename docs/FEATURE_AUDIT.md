# Gameplay parity audit

Primary baseline: mactso/Regrowth 8bda8b5ff4b9e456f16384093ea0f0580716b598.
The original Java source is retained in Git history. “Implemented” describes code;
only tests explicitly listed in VALIDATION.md establish observed behavior.
Complete per-feature parity remains a release gate, not a consequence of compilation.

| Original feature | Status | Verification / intentional differences |
|---|---|---|
| Entity intervals, parity, horse multiplier | Implemented | Unit tests: ten eligible checks/s and original probabilities |
| Cow eat/grow split | Implemented | Original >85/100 grow threshold; full statistical/gameplay test pending |
| Eating, healing, baby growth | Implemented | Eating tested in GameTests; legacy overcrowding available only through explicit opt-in (off by default for farm safety) |
| Grass bonemeal | Implemented with safety checks | Vanilla only; loaded neighborhood; comprehensive vegetation tests pending |
| Biome-sensitive saplings | Implemented | Existing sapling spacing tested; biome selection tests pending |
| Creeper tall grass | Implemented | Vanilla bonemeal; loaded neighborhood only |
| Hostile torch stumbling | Implemented | Original configured action; protected list applies |
| Coral | Adapted | Original source block update is a no-op; fans now require survivable water positions; coral probability/placement parity unverified |
| Mushrooms | Adapted | Giant/custom 3% small giant/shroomlight generation retained with clear-volume checks and temporary soil restoration; runtime shape/probability parity unverified |
| Roads | Adapted | Patch/snow/bump/pothole/slope handling; stricter natural-road substrate and bounded two-block height adjustment protect builds |
| Lighting | Implemented | Floor/wall torch, profession bonus, wall spacing; floor torch/feature flag tested |
| Village walls | Adapted | Legacy square, biome materials, bell control, gateways, loaded-area overlap checks, mason corners; no automatic control-block creation |
| Personal fences | Implemented | Optional original `p` action; absent from default action string; loaded-area home-overlap checks; geometry parity unverified |
| Cleric/toolsmith healing | Implemented | Profession/time windows retained; full runtime tests pending |
| Farmer repair | Implemented | Adjacent wet farmland requirement; runtime test pending |
| Beekeeper flowers | Implemented | Profession ID path matching; runtime test pending |
| Leaf/cactus trimming | Implemented | Eight directions, young-villager escape, persistent player leaves excluded |
| Powder snow rescue | Implemented | Bounded direct edits with protection checks; runtime test pending |
| Farmland trample | Baseline defect identified | Original returns without cancellation; no change to vanilla trample behavior or phantom fix claimed |
| Commands | Implemented | Debug level and eight-block entity raycast; console support; permission/interaction tests pending |
| Config/resources | Preserved identifiers | Historical duplicate key documented; missing/partial/malformed values, TOML write cycles and exact backups tested |
| Adaptive village planning/profiles/commands | Not incorporated | Reference evaluated only; would change original world semantics/defaults |
| World SavedData | Original has none | Reference format not interpreted/migrated; no fabricated save-data coverage |

## Server safety and performance

Original static foot/ground/biome fields and global torch coordinates can leak
state across calls/dimensions. New event calls keep positions and block states local.
A bounded 4096-entry entity-type classification cache stores rules/misses without
world references; configuration reload publishes a volatile immutable snapshot; the game thread
clears the cache on its next tick (and shutdown clears it). Dimension-specific
state never resides in that cache. Pure bounded-cache utility tests exist, but no
village/world cache is installed: fixed-square geometry needs no expensive planner.

Server/player/parity checks precede rule resolution. Biome work/scans occur only
for relevant selected actions. Scans have explicit bounds, early exits, and loaded
chunk checks; POI overlap queries happen only on a candidate wall perimeter and an
already loaded area. No asynchronous world mutations are used. Vanilla generators
are restricted to loaded neighborhoods; giant mushroom generation additionally
requires a clear, protected-block-free volume. Test handlers are enabled only in
explicit development GameTest runs.

The microbenchmark in VALIDATION.md measures only the pure parity gate on this
machine. No relative speedup, allocation reduction, TPS improvement or long-running
memory bound has been measured. A representative sustained entity/village workload
and claim-mod interoperability tests are still required before production use.
