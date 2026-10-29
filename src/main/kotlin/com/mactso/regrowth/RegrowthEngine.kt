package com.mactso.regrowth

import com.mactso.regrowth.core.MobRule
import com.mactso.regrowth.core.Rules
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.village.poi.PoiManager
import net.minecraft.world.entity.ai.village.poi.PoiTypes
import net.minecraft.world.entity.animal.equine.AbstractHorse
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.phys.AABB
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.abs
import kotlin.math.sin

/** One owner per mod instance. No world references or shared entity scratch fields. */
class RegrowthEngine(private val eventRoll: (ServerLevel) -> Double = { it.random.nextDouble() }) {
    private data class Configuration(
        val rules: Map<String, MobRule> = emptyMap(),
        val foundations: Set<String> = emptySet(),
        val protected: Set<String> = emptySet(),
        val walls: Map<String, WallRule> = emptyMap()
    )
    @Volatile private var configuration = Configuration()
    // Only the game thread mutates this cache. Config watcher threads publish snapshots.
    private var cachedConfiguration = configuration
    private val typeRules = java.util.IdentityHashMap<EntityType<*>, java.util.Optional<MobRule>>()
    private val foundations: Set<String> get() = configuration.foundations
    private val protected: Set<String> get() = configuration.protected
    private val walls: Map<String, WallRule> get() = configuration.walls
    val ruleCount: Int get() = configuration.rules.size
    fun reload(mobRules: String = RegrowthConfig.mobs.get()) {
        val rules = Rules.parseMobs(mobRules) { Regrowth.LOGGER.warn(it) }
        val foundations = RegrowthConfig.foundations.get().toSet()
        val protected = RegrowthConfig.protectedBlocks.get().toSet()
        val walls = RegrowthConfig.biomeWalls.get().split(';').mapNotNull { row ->
            val p = row.split(',').map(String::trim)
            val diameter = p.getOrNull(1)?.toIntOrNull()
            if (p.size != 4 || diameter == null || !Rules.validId(p[2]) || !Rules.validId(p[3])) null
            else p[0].lowercase() to WallRule(Rules.wallRadius(diameter), p[2], p[3])
        }.toMap()
        configuration = Configuration(rules, foundations, protected, walls)
    }
    fun clear() { typeRules.clear(); cachedConfiguration = configuration }

    fun tick(entity: LivingEntity, level: ServerLevel) {
        val current = configuration
        if (cachedConfiguration !== current) { typeRules.clear(); cachedConfiguration = current }
        val cached = typeRules[entity.type]
        val rule = if (cached != null) cached.orElse(null) else {
            val resolved = current.rules[BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()]
            if (typeRules.size < 4096) typeRules[entity.type] = java.util.Optional.ofNullable(resolved)
            resolved
        } ?: return
        val base = entity.blockPosition()
        val foot = if (entity.y == base.y.toDouble()) base else base.above()
        if (!loaded(level, foot) || !loaded(level, foot.below())) return
        val feet = level.getBlockState(foot)
        if (feet.block is WoolCarpetBlock) return
        val ground = level.getBlockState(foot.below())
        if (ground.isAir && rule.action != "stumble") return
        // First-200-minute village boost, with identical tick/parity and age limits.
        if (entity is Villager && RegrowthConfig.villageChanges.get() && level.gameTime % 12L == 0L) {
            val chunk = level.chunkSource.getChunkNow(base.x shr 4, base.z shr 4)
            if (chunk != null && chunk.inhabitedTime < 480000L) {
                if ('r' in rule.action) road(entity, level, foot)
                if ('w' in rule.action) wall(entity, level, foot, 't' in rule.action)
            }
        }
        val eatingHorse = entity is AbstractHorse && entity.isEating
        if (eventRoll(level) > rule.probability(eatingHorse)) return
        if (entity is Villager) {
            if (RegrowthConfig.villageChanges.get()) village(entity, level, foot, rule.action)
            return
        }
        if (rule.action in ordinaryActions && entity is Animal && RegrowthConfig.legacyOvercrowding.get()) {
            val excess = level.getEntitiesOfClass(Animal::class.java, AABB(base).inflate(3.0, 2.0, 3.0)) { it.type === entity.type }.size - 16
            if (excess > 16) entity.setPos(entity.x, -66.0, entity.z)
            else if (excess > 0) entity.hurtServer(level, level.damageSources().inWall(), excess + level.random.nextFloat() / 6f)
        }
        when (rule.action) {
            "eat" -> eat(entity, level, foot)
            "grow" -> grow(level, foot)
            "both" -> if (level.random.nextDouble() * 100 > 85) grow(level, foot) else eat(entity, level, foot)
            "reforest" -> reforest(level, foot)
            "tall" -> if (feet.block is TallGrassBlock) bonemeal(level, foot)
            "stumble" -> if (feet.block is TorchBlock || feet.block is WallTorchBlock) remove(level, foot, true)
            "coral" -> coral(level, foot)
            "mushroom" -> mushroom(level, foot)
            "cut" -> if (plant(feet)) remove(level, foot)
        }
    }

    private fun loaded(level: ServerLevel, pos: BlockPos): Boolean =
        !level.isOutsideBuildHeight(pos) && level.chunkSource.getChunkNow(pos.x shr 4, pos.z shr 4) != null

    private fun safe(level: ServerLevel, pos: BlockPos): Boolean {
        if (!loaded(level, pos)) return false
        val state = level.getBlockState(pos)
        return !state.hasBlockEntity() && BuiltInRegistries.BLOCK.getKey(state.block).toString() !in protected
    }
    private fun place(level: ServerLevel, pos: BlockPos, state: BlockState, eligible: (BlockState) -> Boolean = { it.isAir }): Boolean {
        if (!safe(level, pos) || !eligible(level.getBlockState(pos))) return false
        if (!state.canSurvive(level, pos)) return false
        return level.setBlockAndUpdate(pos, state)
    }
    private fun remove(level: ServerLevel, pos: BlockPos, drops: Boolean = false): Boolean =
        safe(level, pos) && level.destroyBlock(pos, drops)
    private fun plant(state: BlockState): Boolean = state.block is TallGrassBlock || state.block is DoublePlantBlock ||
        state.block is FlowerBlock || state.block is ShortDryGrassBlock || state.block is TallDryGrassBlock || state.`is`(BlockTags.FLOWERS)
    private fun grass(state: BlockState): Boolean = state.block is GrassBlock ||
        BuiltInRegistries.BLOCK.getKey(state.block).toString() == "byg:meadow_grass_block"

    private fun eat(entity: LivingEntity, level: ServerLevel, pos: BlockPos) {
        if (!grass(level.getBlockState(pos.below())) || !plant(level.getBlockState(pos))) return
        if (!remove(level, pos)) return
        if (entity is Animal) {
            val nearby = level.getEntitiesOfClass(Animal::class.java, AABB(pos).inflate(2.0, 2.0, 2.0)) { it.type === entity.type }.size
            if (nearby >= 9 && place(level, pos.below(), Blocks.DIRT_PATH.defaultBlockState(), ::grass)) {
                entity.hurtServer(level, level.damageSources().inWall(), .25f)
            }
        }
        if (level.random.nextDouble() >= RegrowthConfig.eatingHeals.get()) return
        if (entity is AgeableMob && entity.isBaby) entity.age += 30
        if (entity.health < entity.maxHealth) entity.addEffect(MobEffectInstance(MobEffects.INSTANT_HEALTH, 1))
    }
    private fun grow(level: ServerLevel, pos: BlockPos) {
        if (level.getBlockState(pos).isAir && grass(level.getBlockState(pos.below()))) bonemeal(level, pos.below())
    }
    private fun bonemeal(level: ServerLevel, pos: BlockPos) {
        if (!safe(level, pos)) return
        // Vanilla generators can spread: require their bounded neighborhood already loaded.
        if (!areaLoaded(level, pos, 8, 8)) return
        // A generator can write neighbors as well as its source; honor protection there too.
        if (protected.isNotEmpty() && count(level, pos, 8, 8, 1) { BuiltInRegistries.BLOCK.getKey(it.block).toString() in protected } > 0) return
        val state = level.getBlockState(pos)
        val block = state.block as? BonemealableBlock ?: return
        if (BuiltInRegistries.BLOCK.getKey(state.block).namespace != "minecraft") return
        MinecraftPlatform.bonemeal(level, pos, state, block)
    }
    private fun areaLoaded(level: ServerLevel, pos: BlockPos, radius: Int, height: Int): Boolean {
        for (x in ((pos.x - radius) shr 4)..((pos.x + radius) shr 4)) {
            for (z in ((pos.z - radius) shr 4)..((pos.z + radius) shr 4)) {
                if (level.chunkSource.getChunkNow(x, z) == null) return false
            }
        }
        return !level.isOutsideBuildHeight(pos.above(height)) && !level.isOutsideBuildHeight(pos.below())
    }
    private inline fun count(level: ServerLevel, pos: BlockPos, radius: Int, height: Int, limit: Int, matches: (BlockState) -> Boolean): Int {
        var count = 0
        val cursor = BlockPos.MutableBlockPos()
        for (y in -height..height) for (x in -radius..radius) for (z in -radius..radius) {
            cursor.set(pos.x + x, pos.y + y, pos.z + z)
            if (loaded(level, cursor) && matches(level.getBlockState(cursor)) && ++count >= limit) return count
        }
        return count
    }
    private fun reforest(level: ServerLevel, pos: BlockPos) {
        val ground = level.getBlockState(pos.below())
        if (!level.getBlockState(pos).isAir || !(grass(ground) || ground.`is`(Blocks.DIRT))) return
        if (level.random.nextDouble() > abs(sin(((pos.y + 64) % 256).toDouble() / 256))) return
        if (count(level, pos, 5, 0, 1) { it.block is SaplingBlock } > 0) return
        val biome = level.getBiome(pos).unwrapKey().map { it.identifier().path }.orElse("")
        val sapling = when {
            "birch" in biome -> Blocks.BIRCH_SAPLING
            "taiga" in biome -> Blocks.SPRUCE_SAPLING
            "savanna" in biome || "desert" in biome -> Blocks.ACACIA_SAPLING
            else -> Blocks.OAK_SAPLING
        }
        if (count(level, pos.above(if (sapling == Blocks.ACACIA_SAPLING) 5 else 4), if (sapling == Blocks.ACACIA_SAPLING) 7 else 4, 0, 1) { it.block is LeavesBlock } > 0) return
        place(level, pos, sapling.defaultBlockState())
    }
    private fun coral(level: ServerLevel, pos: BlockPos) {
        val source = (1..3).map { pos.below(it) }.firstOrNull { loaded(level, it) && level.getBlockState(it).block is CoralBlock } ?: return
        if (count(level, source.above(), 1, 0, 6) { it.block is CoralBlock } > 5) return
        val target = source.above().offset(level.random.nextInt(2) - 1, 0, level.random.nextInt(2) - 1)
        if (!loaded(level, target) || !level.getBlockState(target).`is`(Blocks.WATER)) return
        // Original's block placement is a no-op at source; retain water safety rather than overwrite terrain.
        if (level.random.nextInt(100) > 30) return
        val fans = arrayOf(Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL_FAN, Blocks.TUBE_CORAL_FAN)
        place(level, target, fans[level.random.nextInt(fans.size)].defaultBlockState()) { it.`is`(Blocks.WATER) }
    }
    private fun mushroom(level: ServerLevel, pos: BlockPos) {
        if (!level.getBlockState(pos).isAir || level.canSeeSky(pos)) return
        val temp = level.getBiome(pos).value().baseTemperature.toDouble()
        if (temp !in RegrowthConfig.mushroomMinTemp.get()..RegrowthConfig.mushroomMaxTemp.get()) return
        val seeded = java.util.Random(abs(pos.x * 31).toLong() + abs(pos.z * 11) + abs(pos.y * 7))
        seeded.nextDouble(); if (seeded.nextDouble() < .75) return
        if (count(level, pos, 4, 1, 4) { it.block is MushroomBlock } > 3) return
        val ground = level.getBlockState(pos.below())
        if (!(ground.`is`(BlockTags.BASE_STONE_OVERWORLD) || ground.`is`(BlockTags.DIRT) || ground.block is HugeMushroomBlock)) return
        if (level.hasNearbyAlivePlayer(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), 12.0)) return
        val block = if (level.random.nextDouble() > .75) Blocks.RED_MUSHROOM else Blocks.BROWN_MUSHROOM
        val density = maxOf(RegrowthConfig.mushroomXDensity.get(), RegrowthConfig.mushroomZDensity.get())
        if (count(level, pos, density, 1, 1) { it.block is HugeMushroomBlock } > 0) return
        if (!clearGrowthVolume(level, pos)) {
            place(level, pos, block.defaultBlockState())
            return
        }
        if (level.random.nextDouble() < .03) {
            val cap = if (block == Blocks.RED_MUSHROOM) Blocks.RED_MUSHROOM_BLOCK else Blocks.BROWN_MUSHROOM_BLOCK
            val diagonal = level.random.nextInt(6) >= 3
            for (y in 0..2) place(level, pos.above(y), Blocks.MUSHROOM_STEM.defaultBlockState())
            for (x in -1..1) for (z in -1..1) {
                if (x == 0 && z == 0) continue
                if (diagonal || x == 0 || z == 0) place(level, pos.offset(x, 2, z), cap.defaultBlockState())
            }
            place(level, pos.above(3), cap.defaultBlockState())
            return
        }
        val originalGround = level.getBlockState(pos.below())
        if (!safe(level, pos.below()) || !safe(level, pos)) return
        level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState())
        try {
            if (!place(level, pos, block.defaultBlockState())) return
            val mushroom = block as MushroomBlock
            mushroom.growMushroom(level, pos, block.defaultBlockState(), level.random)
            if (level.getBlockState(pos).block == block) {
                mushroom.growMushroom(level, pos, block.defaultBlockState(), level.random)
            }
            if (level.getBlockState(pos).block == block) remove(level, pos)
            else if (block == Blocks.RED_MUSHROOM) {
                for (y in 9 downTo 4) {
                    val stem = pos.above(y)
                    if (loaded(level, stem) && level.getBlockState(stem).`is`(Blocks.MUSHROOM_STEM)) {
                        place(level, stem, Blocks.SHROOMLIGHT.defaultBlockState()) { it.`is`(Blocks.MUSHROOM_STEM) }
                        break
                    }
                }
            }
        } finally {
            // Temporary soil substitution must never persist after failure or exception.
            level.setBlockAndUpdate(pos.below(), originalGround)
        }
    }

    private fun clearGrowthVolume(level: ServerLevel, pos: BlockPos): Boolean {
        if (!areaLoaded(level, pos, 8, 12)) return false
        val cursor = BlockPos.MutableBlockPos()
        for (y in 0..12) for (x in -8..8) for (z in -8..8) {
            cursor.set(pos.x + x, pos.y + y, pos.z + z)
            if (!safe(level, cursor) || !level.getBlockState(cursor).isAir) return false
        }
        return true
    }

    private fun village(villager: Villager, level: ServerLevel, pos: BlockPos, action: String) {
        rescuePowderSnow(level, pos)
        if (!villager.onGround() || villager.isSleeping || villager.isBaby) return
        val feet = level.getBlockState(pos)
        if (feet.block is BedBlock || level.getBlockState(pos.below()).block is TorchBlock) return
        val profession = villager.villagerData.profession().unwrapKey().map { it.identifier().path }.orElse("")
        if (profession == "farmer") farm(level, pos.below())
        if ('h' in action) heal(villager, level, profession)
        if (profession == "beekeeper" && pos.x % 6 == 0 && pos.z % 7 == 0 && grass(level.getBlockState(pos.below()))) {
            if (Direction.Plane.HORIZONTAL.any { loaded(level, pos.below().relative(it)) && level.getBlockState(pos.below().relative(it)).`is`(Blocks.DIRT_PATH) }) {
                place(level, pos, Blocks.AZURE_BLUET.defaultBlockState())
            }
        }
        if ('c' in action && plant(feet)) remove(level, pos)
        if ('v' in action) {
            val facing = abs(kotlin.math.round(villager.yRot / 45f).toInt()) % 8
            val offset = headings[facing]
            trim(level, pos.offset(offset[0], 0, offset[1]))
            trim(level, pos.offset(offset[0], 1, offset[1]))
            if (villager.tickCount < 199) { trim(level, pos); trim(level, pos.above()) }
        }
        if ('r' in action) road(villager, level, pos)
        if ('w' in action) wall(villager, level, pos, 't' in action)
        if ('p' in action) homeFence(villager, level, pos, 't' in action)
        if ('t' in action) light(villager, level, pos, profession)
    }
    private fun trim(level: ServerLevel, pos: BlockPos) {
        if (!loaded(level, pos)) return
        val state = level.getBlockState(pos)
        if ((state.block is LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) || state.block is CactusBlock) remove(level, pos)
    }
    private fun farm(level: ServerLevel, pos: BlockPos) {
        if (!level.getBlockState(pos).`is`(BlockTags.DIRT)) return
        val wet = Direction.Plane.HORIZONTAL.any {
            val p = pos.relative(it)
            loaded(level, p) && level.getBlockState(p).block is FarmlandBlock && level.getBlockState(p).getValue(FarmlandBlock.MOISTURE) > 1
        }
        if (wet) place(level, pos, Blocks.FARMLAND.defaultBlockState()) { it.`is`(BlockTags.DIRT) }
    }
    private fun heal(villager: Villager, level: ServerLevel, profession: String) {
        if (level.overworldClockTime % 24000 !in 9000..11000) return
        if (profession != "cleric" && profession != "toolsmith") return
        val box = villager.boundingBox.inflate(if (profession == "cleric") 4.0 else 6.0, 2.0, 4.0)
        val target = level.getEntitiesOfClass(LivingEntity::class.java, box) {
            it.health < it.maxHealth && !it.hasEffect(MobEffects.REGENERATION) &&
                if (profession == "toolsmith") BuiltInRegistries.ENTITY_TYPE.getKey(it.type).path == "iron_golem"
                else (it is Villager || (it is Player && villager.getPlayerReputation(it) >= 0))
        }.firstOrNull() ?: return
        target.addEffect(MobEffectInstance(MobEffects.REGENERATION, villager.villagerData.level() * 51), villager)
    }
    private fun road(villager: Villager, level: ServerLevel, pos: BlockPos) {
        if (!level.canSeeSky(pos)) return
        val ground = pos.below()
        val biome = level.getBiome(pos).unwrapKey().map { it.identifier().path }.orElse("")
        val road = if ("desert" in biome) Blocks.ACACIA_PLANKS else Blocks.DIRT_PATH
        val under = level.getBlockState(ground)
        if (level.getBlockState(pos).block is SnowLayerBlock) remove(level, pos)
        if (!level.getBlockState(pos).isAir) return
        if (grass(under) || under.`is`(Blocks.DIRT)) {
            var adjacent = 0
            for (direction in Direction.Plane.HORIZONTAL) for (dy in -3..1) {
                val neighbor = pos.relative(direction).above(dy)
                if (loaded(level, neighbor) && level.getBlockState(neighbor).`is`(road)) adjacent++
            }
            if (adjacent >= 3) place(level, ground, road.defaultBlockState()) { grass(it) || it.`is`(Blocks.DIRT) }
            return
        }
        if (!under.`is`(road)) return
        // Only manipulate natural road substrate, never arbitrary construction material.
        if (!safe(level, ground) || !loaded(level, ground.below())) return
        val neighbors = Direction.Plane.HORIZONTAL.map { ground.relative(it) }
        if (neighbors.all { loaded(level, it) && loaded(level, it.below()) && level.getBlockState(it).isAir && level.getBlockState(it.below()).`is`(road) }) {
            if (place(level, ground.below(), road.defaultBlockState()) { it.`is`(BlockTags.DIRT) || it.`is`(road) }) remove(level, ground)
            return
        }
        if (Direction.Plane.HORIZONTAL.all { loaded(level, pos.relative(it)) && level.getBlockState(pos.relative(it)).`is`(road) }) {
            if (place(level, pos, road.defaultBlockState())) villager.setDeltaMovement(0.0, .4, 0.0)
            return
        }
        if (!villager.onGround() || villager.isBaby) return
        val poiDistance = if ("desert" in biome) 7 else 3
        if (!areaLoaded(level, pos, poiDistance, 2)) return
        val nearPoi = level.poiManager.getInSquare({ true }, pos, poiDistance, PoiManager.Occupancy.ANY).use { it.findAny().isPresent }
        if (nearPoi) return
        for (direction in Direction.Plane.HORIZONTAL) {
            val neighbor = pos.relative(direction)
            if (!loaded(level, neighbor)) continue
            val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, neighbor.x, neighbor.z) - 1
            if (y <= pos.y || y > pos.y + 2) continue
            val higher = BlockPos(neighbor.x, y, neighbor.z)
            if (loaded(level, higher) && level.getBlockState(higher).`is`(road)) {
                if (place(level, pos, road.defaultBlockState())) villager.setDeltaMovement(0.0, .4, 0.0)
                return
            }
        }
    }
    private fun light(villager: Villager, level: ServerLevel, pos: BlockPos, profession: String) {
        val bonus = if (villager.villagerData.level() >= 3 && profession in setOf("toolsmith", "weaponsmith", "armorer", "fisherman", "butcher")) 1 else 0
        val limit = RegrowthConfig.torchLightLevel.get() + bonus
        val head = pos.above()
        val facing = villager.direction
        if (loaded(level, head) && loaded(level, head.relative(facing)) && level.getBrightness(LightLayer.BLOCK, head) < limit) {
            val wallTorch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing.opposite)
            if (place(level, head, wallTorch)) return
        }
        if (level.getBrightness(LightLayer.BLOCK, pos) >= limit) return
        val torch = resolve(RegrowthConfig.torchBlock.get()) ?: return
        place(level, pos, torch.defaultBlockState())
    }
    private fun rescuePowderSnow(level: ServerLevel, pos: BlockPos) {
        if (!level.getBlockState(pos).`is`(Blocks.POWDER_SNOW)) return
        var layers = 2
        for (dy in 1..2) {
            val above = pos.above(dy)
            if (loaded(level, above) && level.getBlockState(above).`is`(Blocks.POWDER_SNOW) && remove(level, above)) layers += 2
        }
        place(level, pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers)) { it.`is`(Blocks.POWDER_SNOW) }
    }
    private fun homeFence(villager: Villager, level: ServerLevel, pos: BlockPos, placeLights: Boolean) {
        val meeting = villager.brain.getMemory(MemoryModuleType.MEETING_POINT).orElse(null) ?: return
        val home = villager.brain.getMemory(MemoryModuleType.HOME).orElse(null) ?: return
        if (meeting.dimension() != level.dimension() || home.dimension() != level.dimension()) return
        if (!loaded(level, meeting.pos().above()) || level.getBlockState(meeting.pos().above()).block !is WallBlock) return
        val rule = wallRule(level, pos) ?: return
        if (abs(pos.x - meeting.pos().x) <= rule.radius + 1 && abs(pos.z - meeting.pos().z) <= rule.radius + 1) return
        val radius = ((rule.radius + 1) / 4 - 1).coerceAtLeast(1)
        if (!Rules.onPerimeter(pos.x - home.pos().x, pos.z - home.pos().z, radius)) return
        if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.below()).block).toString() !in foundations) return
        val perpendicular = if (abs(pos.x - home.pos().x) == radius) pos.z - home.pos().z else pos.x - home.pos().x
        if (perpendicular == 0) return
        if (!areaLoaded(level, pos, 17, 1)) return
        val nearbyHome = level.poiManager.getInSquare({ it.`is`(PoiTypes.HOME) }, pos, 17, PoiManager.Occupancy.ANY).use { homes ->
            homes.anyMatch { it.pos != home.pos() && abs(pos.x - it.pos.x) < radius && abs(pos.z - it.pos.z) < radius }
        }
        if (nearbyHome) return
        val fence = resolve(rule.fence) ?: return
        if (place(level, pos, fence.defaultBlockState()) { it.isAir || plant(it) }) {
            val spacing = (radius / 4).coerceAtLeast(1)
            val x = abs(pos.x - home.pos().x)
            val z = abs(pos.z - home.pos().z)
            if (placeLights && ((x == radius && z % spacing == 1) || (z == radius && x % spacing == 1) || (x == radius && z == radius))) {
                place(level, pos.above(), Blocks.TORCH.defaultBlockState())
            }
            villager.setDeltaMovement(0.0, .55, 0.0)
        }
    }
    private fun wall(villager: Villager, level: ServerLevel, pos: BlockPos, placeLights: Boolean) {
        val meeting = villager.brain.getMemory(MemoryModuleType.MEETING_POINT).orElse(null) ?: return
        if (meeting.dimension() != level.dimension()) return
        val center = meeting.pos()
        if (!loaded(level, center) || !loaded(level, center.above()) || !level.getBlockState(center).`is`(Blocks.BELL)) return
        val control = resolve(RegrowthConfig.controlBlock.get()) ?: return
        if (control == Blocks.AIR) return
        // Never create a new control block over an existing world's bell.
        if (!level.getBlockState(center.above()).`is`(control)) return
        val rule = wallRule(level, pos) ?: return
        if (!Rules.onPerimeter(pos.x - center.x, pos.z - center.z, rule.radius)) return
        if (!villager.onGround() || level.getBrightness(LightLayer.SKY, pos) < 13) return
        if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.below()).block).toString() !in foundations) return
        if (!loaded(level, pos.above()) || !safe(level, pos.below())) return
        val sideCoordinate = if (abs(pos.x - center.x) == rule.radius) pos.z - center.z else pos.x - center.x
        if (sideCoordinate == 0) return // retain gateways
        if (!areaLoaded(level, pos, 41, 1)) return
        val overlaps = level.poiManager.getInSquare({ it.`is`(PoiTypes.MEETING) }, pos, 41, PoiManager.Occupancy.ANY).use { points ->
            points.anyMatch { it.pos != center && abs(pos.x - it.pos.x) < rule.radius && abs(pos.z - it.pos.z) < rule.radius }
        }
        if (overlaps) return
        val wall = resolve(rule.wall) ?: return
        if (place(level, pos, wall.defaultBlockState()) { it.isAir || plant(it) || it.block is SnowLayerBlock }) {
            val spacing = ((rule.radius + 1) / 4).coerceAtLeast(1)
            val x = abs(pos.x - center.x)
            val z = abs(pos.z - center.z)
            if (placeLights && ((x == rule.radius && z % spacing == 1) || (z == rule.radius && x % spacing == 1) || (x == rule.radius && z == rule.radius))) {
                place(level, pos.above(), Blocks.TORCH.defaultBlockState())
            }
            val job = villager.villagerData.profession().unwrapKey().map { it.identifier().path }.orElse("")
            if (job == "mason") masonCorner(level, center, rule)
            villager.setDeltaMovement(0.0, .55, 0.0)
        }
    }
    private fun masonCorner(level: ServerLevel, center: BlockPos, rule: WallRule) {
        val wall = resolve(rule.wall) ?: return
        for (dx in listOf(rule.radius, -rule.radius - 1)) for (dz in listOf(rule.radius, -rule.radius - 1)) {
            val corner = center.offset(dx, 0, dz)
            if (!loaded(level, corner)) continue
            val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, corner.x, corner.z)
            val target = BlockPos(corner.x, y, corner.z)
            if (!loaded(level, target) || !loaded(level, target.below()) || !loaded(level, target.above())) continue
            if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(target.below()).block).toString() !in foundations) continue
            if (!safe(level, target.below())) continue
            if (place(level, target, wall.defaultBlockState()) { it.isAir || plant(it) || it.block is SnowLayerBlock }) {
                place(level, target.above(), Blocks.LANTERN.defaultBlockState())
                return
            }
        }
    }
    private fun wallRule(level: ServerLevel, pos: BlockPos): WallRule? {
        val biome = level.getBiome(pos).unwrapKey().map { it.identifier().toString() }.orElse("")
        val category = when {
            "desert" in biome -> "desert"; "taiga" in biome -> "taiga"; "savanna" in biome -> "savanna"
            "jungle" in biome -> "jungle"; "forest" in biome -> "forest"; "beach" in biome -> "beach"
            "badlands" in biome -> "mesa"; "snow" in biome || "ice" in biome -> "icy"
            "nether" in biome -> "nether"; "end" in biome -> "the_end"; else -> "plains"
        }
        return walls[biome] ?: walls["minecraft:$category"] ?: walls["regrowth:default"]
    }
    private fun resolve(id: String): Block? {
        val identifier = Identifier.tryParse(id) ?: return null
        if (!BuiltInRegistries.BLOCK.containsKey(identifier)) return null
        return BuiltInRegistries.BLOCK.getValue(identifier)
    }
    companion object {
        private val ordinaryActions = setOf("eat", "grow", "both")
        private val headings = arrayOf(intArrayOf(0, 1), intArrayOf(-1, 1), intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1))
    }
    private data class WallRule(val radius: Int, val wall: String, val fence: String)
}
