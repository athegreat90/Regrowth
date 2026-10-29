package com.mactso.regrowth.core

import java.nio.file.Files
import kotlin.test.*

class ConfigBackupTest {
    @Test fun malformedAndPartialConfigsArePreservedByteForByte() {
        val dir = Files.createTempDirectory("regrowth-test-")
        try {
            val config = dir.resolve("regrowth-common.toml")
            val invalid = "[broken TOML\n\"torchLightLevel \"=3\n".toByteArray()
            Files.write(config, invalid)
            val backup = assertNotNull(ConfigBackup.preserve(config))
            assertContentEquals(invalid, Files.readAllBytes(config))
            assertContentEquals(invalid, Files.readAllBytes(backup))
            repeat(3) { assertEquals(backup, ConfigBackup.preserve(config)) }
            Files.writeString(config, "[partial]\nvalue=7\n")
            val second = ConfigBackup.preserve(config)
            assertNotEquals(backup, second)
            assertContentEquals(invalid, Files.readAllBytes(backup))
        } finally { Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    @Test fun absentConfigIsNotCreated() {
        val dir = Files.createTempDirectory("regrowth-test-")
        try { assertNull(ConfigBackup.preserve(dir.resolve("missing.toml"))) }
        finally { Files.delete(dir) }
    }
}
