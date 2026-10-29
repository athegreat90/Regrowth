package com.mactso.regrowth

import com.electronwill.nightconfig.core.CommentedConfig
import com.electronwill.nightconfig.toml.TomlParser
import com.electronwill.nightconfig.toml.TomlWriter
import com.mactso.regrowth.core.Rules
import kotlin.test.*

class CompatibilityTest {
    @Test fun missingConfigReceivesLegacyDefaults() {
        val config = CommentedConfig.inMemory()
        RegrowthConfig.spec.correct(config)
        assertEquals(.99, config.get<Double>(listOf("Regrowth Control Values", "eatingHeals")))
        assertEquals(3, config.get<Int>(listOf("Regrowth Control Values", "torchLightLevel ")))
        assertEquals(LegacyDefaults.defaultRegrowthMobs6464, config.get<String>(listOf("Regrowth Mobs 6464", "defaultRegrowthMobsActual")))
        assertEquals(14, Rules.parseMobs(LegacyDefaults.defaultRegrowthMobs6464).size)
    }
    @Test fun partialConfigKeepsExistingPreferences() {
        val config = CommentedConfig.inMemory()
        config.set<Int>(listOf("Regrowth Control Values", "debugLevel"), 2)
        config.set<String>(listOf("Regrowth Mobs 6464", "defaultRegrowthMobsActual"), "minecraft:cow,both,601.0;")
        RegrowthConfig.spec.correct(config)
        repeat(3) { RegrowthConfig.spec.correct(config) }
        assertEquals(2, config.get<Int>(listOf("Regrowth Control Values", "debugLevel")))
        assertEquals("minecraft:cow,both,601.0;", config.get<String>(listOf("Regrowth Mobs 6464", "defaultRegrowthMobsActual")))
    }
    @Test fun malformedValuesGetBoundedDefaults() {
        val config = CommentedConfig.inMemory()
        config.set<String>(listOf("Regrowth Control Values", "eatingHeals"), "broken")
        config.set<Int>(listOf("Regrowth Control Values", "torchLightLevel "), -1)
        RegrowthConfig.spec.correct(config)
        assertEquals(.99, config.get<Double>(listOf("Regrowth Control Values", "eatingHeals")))
        assertEquals(0, config.get<Int>(listOf("Regrowth Control Values", "torchLightLevel ")))
    }
    @Test fun historicalTomlKeysSurviveRepeatedWriting() {
        val original = """
            ["Regrowth Control Values"]
            debugLevel=1
            eatingHeals=0.7
            "torchLightLevel "=5
            "mushroomXDensity "=8
            ["Regrowth Mobs 6464"]
            defaultRegrowthMobsActual="minecraft:cow,both,601.0;"
        """.trimIndent()
        var config = TomlParser().parse(original)
        repeat(5) {
            RegrowthConfig.spec.correct(config)
            config = TomlParser().parse(TomlWriter().writeToString(config))
            assertEquals(5, config.get<Int>(listOf("Regrowth Control Values", "torchLightLevel ")))
            assertEquals(8, config.get<Int>(listOf("Regrowth Control Values", "mushroomXDensity ")))
            assertEquals(.7, config.get<Double>(listOf("Regrowth Control Values", "eatingHeals")))
            assertEquals("minecraft:cow,both,601.0;", config.get<String>(listOf("Regrowth Mobs 6464", "defaultRegrowthMobsActual")))
        }
    }
}
