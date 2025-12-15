package com.je.fontsmanager.samsung.util

import java.io.File

object CacheCleanupUtils {
    fun cleanup(cacheDir: File, excludeFiles: List<File> = emptyList()) {
        try {
            val currentTime = System.currentTimeMillis()
            val maxAgeMs = 2 * 60 * 1000L
            val excludePaths = excludeFiles.map { it.absolutePath }.toSet()
            
            cacheDir.listFiles()?.forEach { file ->
                if (file.absolutePath in excludePaths) {
                    return@forEach
                }
                val isOldFile = (currentTime - file.lastModified()) > maxAgeMs
                when {
                    file.name.startsWith("temp_") && file.name.endsWith(".ttf") -> file.delete()
                    file.name.startsWith("signed_") && file.name.endsWith(".apk") -> file.delete()
                    file.name.startsWith("apk_build_") && file.isDirectory -> file.deleteRecursively()
                    file.name.startsWith("font_preview_") && file.name.endsWith(".ttf") -> file.delete()
                    file.name.startsWith("font_preview_") && file.isDirectory -> file.deleteRecursively()
                    file.isDirectory && file.name.startsWith("temp_") -> file.deleteRecursively()
                    isOldFile && file.name.contains("_") -> file.delete()
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}