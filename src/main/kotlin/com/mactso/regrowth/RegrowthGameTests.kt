package com.mactso.regrowth

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.*
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.Blocks
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Consumer

/** Enabled only by the explicit development GameTest run property. */
object RegrowthGameTests {
    private val functions = DeferredRegister.create(Registries.TEST_FUNCTION, "regrowth")
    private val eat = functions.register("eat_and_protect", java.util.function.Supplier {
        Consumer<GameTestHelper> { helper ->
            val engine = RegrowthEngine { 0.0 }
            engine.reload("minecraft:cow,eat,1.0;")
            helper.setBlock(BlockPos(2, 1, 2), Blocks.GRASS_BLOCK)
            helper.setBlock(BlockPos(2, 2, 2), Blocks.SHORT_GRASS)
            val cow = helper.spawn(MinecraftPlatform.cowType, BlockPos(2, 2, 2))
            engine.tick(cow, helper.level)
            helper.assertBlockPresent(Blocks.AIR, BlockPos(2, 2, 2))
            helper.assertBlockPresent(Blocks.GRASS_BLOCK, BlockPos(2, 1, 2))
            // Consecutive entity processing must not reuse the first entity's positions.
            helper.setBlock(BlockPos(4, 1, 4), Blocks.GRASS_BLOCK)
            helper.setBlock(BlockPos(4, 2, 4), Blocks.CHEST)
            val second = helper.spawn(MinecraftPlatform.cowType, BlockPos(4, 2, 4))
            engine.tick(second, helper.level)
            helper.assertBlockPresent(Blocks.CHEST, BlockPos(4, 2, 4))
            helper.succeed()
        }
    })
    private val protectConfigured = functions.register("configured_protection", java.util.function.Supplier {
        Consumer<GameTestHelper> { helper ->
            val before = RegrowthConfig.protectedBlocks.get()
            try {
                RegrowthConfig.protectedBlocks.set(listOf("minecraft:short_grass"))
                val engine = RegrowthEngine { 0.0 }
                engine.reload("minecraft:cow,eat,1.0;")
                helper.setBlock(BlockPos(2, 1, 2), Blocks.GRASS_BLOCK)
                helper.setBlock(BlockPos(2, 2, 2), Blocks.SHORT_GRASS)
                val cow = helper.spawn(MinecraftPlatform.cowType, BlockPos(2, 2, 2))
                engine.tick(cow, helper.level)
                helper.assertBlockPresent(Blocks.SHORT_GRASS, BlockPos(2, 2, 2))
                // Protect the air above the source too: bonemeal must not bypass neighbor protection.
                RegrowthConfig.protectedBlocks.set(listOf("minecraft:air"))
                engine.reload("minecraft:cow,tall,1.0;")
                engine.tick(cow, helper.level)
                helper.assertBlockPresent(Blocks.SHORT_GRASS, BlockPos(2, 2, 2))
                helper.assertBlockPresent(Blocks.AIR, BlockPos(2, 3, 2))
                helper.succeed()
            } finally { RegrowthConfig.protectedBlocks.set(before) }
        }
    })
    private val boundary = functions.register("unloaded_chunk_boundary", java.util.function.Supplier {
        Consumer<GameTestHelper> { helper ->
            val engine = RegrowthEngine { 0.0 }
            engine.reload("minecraft:cow,eat,1.0;")
            val cow = helper.spawn(MinecraftPlatform.cowType, BlockPos(2, 2, 2))
            try {
                cow.setPos(29_000_000.5, 64.0, 29_000_000.5)
                val chunkX = 29_000_000 shr 4
                check(helper.level.chunkSource.getChunkNow(chunkX, chunkX) == null)
                repeat(100) { engine.tick(cow, helper.level) }
                check(helper.level.chunkSource.getChunkNow(chunkX, chunkX) == null) { "Regrowth loaded a distant chunk" }
                helper.succeed()
            } finally { cow.discard() }
        }
    })
    private val reload = functions.register("rule_reload_and_sapling_spacing", java.util.function.Supplier {
        Consumer<GameTestHelper> { helper ->
            val engine = RegrowthEngine { 0.0 }
            helper.setBlock(BlockPos(2, 1, 2), Blocks.GRASS_BLOCK)
            helper.setBlock(BlockPos(2, 2, 2), Blocks.SHORT_GRASS)
            val cow = helper.spawn(MinecraftPlatform.cowType, BlockPos(2, 2, 2))
            engine.reload("minecraft:cow,eat,1.0;")
            engine.tick(cow, helper.level)
            helper.assertBlockPresent(Blocks.AIR, BlockPos(2, 2, 2))
            helper.setBlock(BlockPos(2, 2, 2), Blocks.SHORT_GRASS)
            engine.clear() // An integrated server can restart while LOCAL/COMMON config remains loaded.
            engine.tick(cow, helper.level)
            helper.assertBlockPresent(Blocks.AIR, BlockPos(2, 2, 2))
            helper.setBlock(BlockPos(2, 2, 2), Blocks.SHORT_GRASS)
            engine.reload("")
            engine.tick(cow, helper.level)
            helper.assertBlockPresent(Blocks.SHORT_GRASS, BlockPos(2, 2, 2))
            helper.setBlock(BlockPos(2, 2, 2), Blocks.AIR)
            helper.setBlock(BlockPos(4, 1, 2), Blocks.GRASS_BLOCK)
            helper.setBlock(BlockPos(4, 2, 2), Blocks.OAK_SAPLING)
            engine.reload("minecraft:cow,reforest,1.0;")
            repeat(100) { engine.tick(cow, helper.level) }
            helper.assertBlockPresent(Blocks.AIR, BlockPos(2, 2, 2))
            helper.assertBlockPresent(Blocks.OAK_SAPLING, BlockPos(4, 2, 2))
            helper.succeed()
        }
    })
    private val lighting = functions.register("village_feature_flag_and_lighting", java.util.function.Supplier {
        Consumer<GameTestHelper> { helper ->
            val before = RegrowthConfig.villageChanges.get()
            try {
                val engine = RegrowthEngine { 0.0 }
                engine.reload("minecraft:villager,t,1.0;")
                helper.setBlock(BlockPos(2, 1, 2), Blocks.GRASS_BLOCK)
                val villager = helper.spawn(MinecraftPlatform.villagerType, BlockPos(2, 2, 2))
                villager.setOnGround(true)
                RegrowthConfig.villageChanges.set(false)
                engine.tick(villager, helper.level)
                helper.assertBlockPresent(Blocks.AIR, BlockPos(2, 2, 2))
                RegrowthConfig.villageChanges.set(true)
                engine.tick(villager, helper.level)
                helper.assertBlockPresent(Blocks.TORCH, BlockPos(2, 2, 2))
                helper.succeed()
            } finally { RegrowthConfig.villageChanges.set(before) }
        }
    })
    fun register(bus: IEventBus) {
        functions.register(bus)
        bus.addListener<RegisterGameTestsEvent> { event ->
            val environment = event.registerEnvironment(Identifier.fromNamespaceAndPath("regrowth", "empty"), TestEnvironmentDefinition.AllOf())
            for (name in listOf("eat_and_protect", "configured_protection", "unloaded_chunk_boundary", "rule_reload_and_sapling_spacing", "village_feature_flag_and_lighting")) {
                val id = Identifier.fromNamespaceAndPath("regrowth", name)
                val data = TestData(environment, Identifier.fromNamespaceAndPath("regrowth", "empty"), 40, 0, true)
                event.registerTest(id, FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id), data))
            }
        }
    }
}
