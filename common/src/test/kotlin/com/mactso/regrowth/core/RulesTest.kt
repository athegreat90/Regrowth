package com.mactso.regrowth.core

import kotlin.test.*

class RulesTest {
    @Test fun legacyMobRowsRoundTripWithoutChangingIntervals() {
        val raw = "minecraft:cow,both,300.0;minecraft:villager,chrwvt,2.0; minecraft:pig,reforest,450.0;"
        val parsed = Rules.parseMobs(raw)
        assertEquals(3, parsed.size)
        repeat(50) { assertEquals(parsed, Rules.parseMobs(Rules.serializeMobs(parsed))) }
        assertEquals(1.0 / 3000, parsed.getValue("minecraft:cow").probability())
    }
    @Test fun malformedRowsDoNotPoisonValidRows() {
        val warnings = mutableListOf<String>()
        val parsed = Rules.parseMobs("garbage;minecraft:cow,both,NaN;minecraft:pig,eat,Infinity;minecraft:bee,grow,0;minecraft:horse,eat,-3;X:Y,grow,4;minecraft:bat,wrong,3", warnings::add)
        assertEquals(setOf("minecraft:bee", "minecraft:horse"), parsed.keys)
        assertEquals(5, warnings.size)
        assertTrue(parsed.values.all { it.seconds == 1.0 })
    }
    @Test fun absentAndEmptyRulesRemainEmpty() { assertTrue(Rules.parseMobs("").isEmpty()) }
    @Test fun eachEntityGetsTenChecksPerSecond() {
        for (id in listOf(0, 1, 31, Int.MAX_VALUE)) {
            assertEquals(10, (0L..19L).count { Rules.checkedOnTick(id, it) })
        }
    }
    @Test fun originalHorseMultiplierAndBothSplitAreExplicit() {
        assertEquals(20.0 / 1800, MobRule("eat", 180.0).probability(true))
        assertFailsWith<IllegalArgumentException> { MobRule("grow", Double.NaN) }
    }
    @Test fun cacheExpiresBoundsAndClears() {
        val cache = BoundedCache<String, Int>(2, 10)
        var loads = 0
        assertEquals(1, cache.get("a", 0) { ++loads })
        assertEquals(1, cache.get("a", 9) { ++loads })
        assertEquals(2, cache.get("a", 10) { ++loads })
        cache.get("b", 10) { ++loads }; cache.get("c", 10) { ++loads }
        assertEquals(2, cache.size)
        assertEquals(5, cache.get("a", 11) { ++loads })
        cache.clear(); assertEquals(0, cache.size)
    }
    @Test fun villageGeometryUsesLegacySquare() {
        assertEquals(19, Rules.wallRadius(40))
        assertTrue(Rules.onPerimeter(19, -19, 19))
        assertFalse(Rules.onPerimeter(20, 0, 19))
        assertFalse(Rules.onPerimeter(18, 18, 19))
    }
}
