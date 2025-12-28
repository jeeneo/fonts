package com.je.fontsmanager.samsung.util

import java.io.File

object CacheCleanupUtils {

    private fun safeDelete(file: File?) {
        try { if (file != null && file.exists()) file.delete() } catch (_: Exception) {}
    }

    private fun safeDeleteRecursively(file: File?) {
        try {
            if (file == null) return
            if (!file.exists()) return
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (_: Exception) {}
    }

    fun deleteFiles(vararg files: File?) {
        files.forEach { safeDelete(it) }
    }

    fun deleteRecursivelyIfExists(file: File?) { safeDeleteRecursively(file) }

    fun cleanup(cacheDir: File, excludeFiles: List<File> = emptyList()) {
        try {
            val excludePaths = excludeFiles.map { it.absolutePath }.toSet()
            cacheDir.listFiles()?.forEach { file ->
                try {
                    if (file.absolutePath in excludePaths) return@forEach
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
