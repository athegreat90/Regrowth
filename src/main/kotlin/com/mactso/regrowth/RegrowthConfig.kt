package com.mactso.regrowth

import com.mactso.regrowth.core.Rules
import net.neoforged.neoforge.common.ModConfigSpec

/** Retains the historical TOML sections, even the keys containing trailing spaces. */
object RegrowthConfig {
    private val builder = ModConfigSpec.Builder()
    init { builder.push("Regrowth Control Values") }
    val debugLevel = builder.defineInRange("debugLevel", 0, 0, 2)
    val eatingHeals = builder.defineInRange("eatingHeals", .99, 0.0, 1.0)
    val torchLightLevel = builder.defineInRange("torchLightLevel ", 3, 0, 10)
    // The original declares mushroomXDensity twice; the later value (6) wins.
    val mushroomXDensity = builder.defineInRange("mushroomXDensity ", 6, 3, 11)
    val mushroomZDensity = builder.defineInRange("mushroomZDensity ", 6, 3, 11)
    val mushroomMinTemp = builder.defineInRange("mushroomMinTemp", .2, -2.0, 2.0)
    val mushroomMaxTemp = builder.defineInRange("mushroomMaxTemp", 1.2, -2.0, 2.0)
    val controlBlock = builder.define("playerWallControlBlockString", "minecraft:cobblestone_wall")
    val torchBlock = builder.define("torchBlockString", "minecraft:torch")
    init { builder.pop(); builder.push("Regrowth Mobs 6464") }
    val mobs = builder.define("defaultRegrowthMobsActual", LegacyDefaults.defaultRegrowthMobs6464)
    init { builder.pop(); builder.push("Regrowth Wall Foundations") }
    val foundations = builder.defineList("wallFoundationsList", listOf(
        "minecraft:grass_block", "minecraft:sand", "minecraft:red_sand", "minecraft:netherrack",
        "minecraft:sandstone", "minecraft:podzol", "minecraft:dirt", "minecraft:stone", "minecraft:coarse_dirt"
    )) { it is String && Rules.validId(it) }
    init { builder.pop(); builder.push("Regrowth Biome Wall Data 6464") }
    val biomeWalls = builder.define("defaultBiomeWallDataActual", LegacyDefaults.defaultBiomeWallData6464)
    init { builder.pop(); builder.push("Regrowth Safety") }
    val villageChanges = builder.comment("Original village maintenance. Back up existing worlds before enabling.")
        .define("villageChanges", true)
    val legacyOvercrowding = builder.comment("Opt-in legacy animal overcrowding damage/void teleport. Disabled to protect existing farms.").define("legacyOvercrowding", false)
    val protectedBlocks = builder.defineList("protectedBlocks", emptyList<String>()) { it is String && Rules.validId(it) }
    init { builder.pop() }
    val spec: ModConfigSpec = builder.build()
}
