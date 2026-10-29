package com.mactso.regrowth

import com.mactso.regrowth.core.Rules
import com.mactso.regrowth.core.ConfigBackup
import net.neoforged.fml.loading.FMLPaths
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.config.ModConfigEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import org.slf4j.LoggerFactory

@Mod("regrowth")
class Regrowth(bus: IEventBus, container: ModContainer) {
    private val engine = RegrowthEngine()
    init {
        ConfigBackup.preserve(FMLPaths.CONFIGDIR.get().resolve("regrowth-common.toml"))
        container.registerConfig(MinecraftPlatform.configType, RegrowthConfig.spec, "regrowth-common.toml")
        bus.addListener<ModConfigEvent.Loading> { if (it.config.spec === RegrowthConfig.spec) engine.reload() }
        bus.addListener<ModConfigEvent.Reloading> { if (it.config.spec === RegrowthConfig.spec) engine.reload() }
        NeoForge.EVENT_BUS.addListener<EntityTickEvent.Post> { event ->
            val entity = event.entity
            if (entity !is LivingEntity || entity is Player) return@addListener
            val level = entity.level() as? ServerLevel ?: return@addListener
            if (!Rules.checkedOnTick(entity.id, level.gameTime)) return@addListener
            engine.tick(entity, level)
        }
        NeoForge.EVENT_BUS.addListener<ServerStoppedEvent> { engine.clear() }
        NeoForge.EVENT_BUS.addListener<RegisterCommandsEvent>(::registerCommands)
        if (java.lang.Boolean.getBoolean("regrowth.gametest")) RegrowthGameTests.register(bus)
        LOGGER.info("Regrowth Kotlin NeoForge continuation registered through KotlinLangForge")
    }
    private fun registerCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("regrowth")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("debugLevel")
                .then(Commands.argument("debugLevel", IntegerArgumentType.integer(0, 2)).executes {
                    RegrowthConfig.debugLevel.set(IntegerArgumentType.getInteger(it, "debugLevel")); 1
                }))
            .then(Commands.literal("info").executes {
                val player = it.source.entity as? Player
                val target = if (player == null) null else {
                    val eye = player.eyePosition
                    val direction = player.lookAngle.scale(8.0)
                    val end = eye.add(direction)
                    it.source.level.getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.expandTowards(direction).inflate(1.0)) { e ->
                        e !== player && e.isPickable && e.isAlive
                    }.mapNotNull { e ->
                        e.boundingBox.inflate(.3).clip(eye, end).map { hit -> e to eye.distanceToSqr(hit) }.orElse(null)
                    }.minByOrNull { pair -> pair.second }?.first
                }
                val info = target?.let { e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.type).toString() } ?: "You don't see an entity."
                it.source.sendSuccess({ Component.literal("Regrowth: debug=${RegrowthConfig.debugLevel.get()}, rules=${engine.ruleCount}, looking at: $info") }, false)
                1
            }))
    }
    companion object { val LOGGER = LoggerFactory.getLogger("regrowth") }
}
