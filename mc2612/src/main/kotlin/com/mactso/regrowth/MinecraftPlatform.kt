package com.mactso.regrowth

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.BonemealableBlock
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.fml.config.ModConfig

/** Thin, compile-checked API differences; no reflective compatibility paths. */
object MinecraftPlatform {
    val villagerType = net.minecraft.world.entity.EntityType.VILLAGER
    val cowType = net.minecraft.world.entity.EntityType.COW
    val configType = ModConfig.Type.COMMON
    fun bonemeal(level: ServerLevel, pos: BlockPos, state: BlockState, block: BonemealableBlock) {
        if (block.isValidBonemealTarget(level, pos, state)) {
            block.performBonemeal(level, level.random, pos, state)
        }
    }
}
