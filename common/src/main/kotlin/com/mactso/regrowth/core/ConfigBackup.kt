package com.mactso.regrowth.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Preserve exact bytes before NeoForge performs its normal config correction. */
object ConfigBackup {
    fun preserve(path: Path): Path? {
        if (!Files.exists(path)) return null
        require(Files.isRegularFile(path)) { "Regrowth config is not a regular file: $path" }
        val bytes = Files.readAllBytes(path)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val backup = path.resolveSibling("${path.fileName}.pre-kotlin-$digest.bak")
        if (Files.exists(backup)) {
            check(Files.readAllBytes(backup).contentEquals(bytes)) { "Existing config backup does not match its digest" }
            return backup
        }
        val temporary = Files.createTempFile(path.parent, ".regrowth-backup-", ".tmp")
        try {
            Files.write(temporary, bytes)
            Files.move(temporary, backup, StandardCopyOption.ATOMIC_MOVE)
        } finally { Files.deleteIfExists(temporary) }
        return backup
    }
}
