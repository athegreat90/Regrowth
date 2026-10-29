package com.mactso.regrowth.core

import kotlin.test.*

/** Repeatable local microbenchmark; not a server tick/GC benchmark or an improvement claim. */
class HotPathTest {
    @Volatile private var sink = 0L
    private fun sample(iterations: Int): Long {
        var accepted = 0L
        val start = System.nanoTime()
        for (i in 0 until iterations) if (Rules.checkedOnTick(i * 31, (i / 7).toLong())) accepted++
        sink = accepted
        return System.nanoTime() - start
    }
    @Test fun parityGateCost() {
        repeat(5) { sample(1_000_000) }
        val samples = List(7) { sample(1_000_000) }.sorted()
        assertTrue(sink in 400_000..600_000)
        println("parityGate iterations=1000000 warmups=5 samples=7 medianNs=${samples[3]} nsPerCall=${samples[3] / 1_000_000.0} java=${System.getProperty("java.version")}")
    }
}
