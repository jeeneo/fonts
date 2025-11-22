package com.je.fontsmanager.samsung.builder

import android.content.Context
import android.util.Log
import com.android.apksig.ApkSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.nio.BufferUnderflowException

object FontBuilder {
    private const val TAG = "FontApkBuilder"
    private const val KEYSTORE_FILE = "my-release.p12"
    private const val KEY_ALIAS = "testkey"
    private const val KEYSTORE_PASSWORD = "testkey"
    private const val KEY_PASSWORD = "testkey"

    data class FontConfig(
        val displayName: String,
        val fontName: String,
        val ttfFile: File,
        val boldTtfFile: File? = null
    ) {
        val packageName: String
            get() = "com.monotype.android.font.$fontName"
        val ttfFileName: String
            get() = "$fontName.ttf"
        val ttfBoldFileName: String
            get() = "${fontName}_bold.ttf"
        val xmlFileName: String
            get() = "$fontName.xml"
    }
    suspend fun buildAndSignFontApk(
        context: Context,
        config: FontConfig,
        outputApk: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val workDir = File(context.cacheDir, "apk_build_${System.currentTimeMillis()}")
            workDir.mkdirs()
            val unsignedApk = File(workDir, "unsigned.apk")
            val buildSuccess = buildUnsignedApk(context, config, unsignedApk, workDir)
            if (!buildSuccess) {
                workDir.deleteRecursively()
                return@withContext false
            }
            signApk(context, unsignedApk, outputApk)
            workDir.deleteRecursively()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error building and signing APK", e)
            false
        }
    }

    private suspend fun buildUnsignedApk(
        context: Context,
        config: FontConfig,
        outputApk: File,
        workDir: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val templateApk = File(workDir, "template.apk")
            context.assets.open("template.apk").use { input ->
                FileOutputStream(templateApk).use { output ->
                    input.copyTo(output)
                }
            }
            val extractDir = File(workDir, "extracted")
            extractDir.mkdirs()
            unzipApk(templateApk, extractDir)
            val manifestFile = File(extractDir, "AndroidManifest.xml")
            patchXML(
                manifestFile,
                "com.monotype.android.font.PLACEHOLDER_FONT_NAME_HI_I_SUCK_AT_MAKING_APPS_SPACE_SPACE",
                config.packageName
            )
            val resourcesFile = File(extractDir, "resources.arsc")
            patchARSC(
                resourcesFile,
                "PLACEHOLDER_FONT_DISPLAY_NAME_LONG_STRING_HERE_SPACE_SPACE_SPACE",
                config.displayName
            )
            val assetsXmlFile = File(extractDir, "assets/xml/${config.xmlFileName}")
            assetsXmlFile.parentFile?.mkdirs()
            createFontXml(assetsXmlFile, config)
            val fontsDir = File(extractDir, "assets/fonts")
            fontsDir.mkdirs()
            val destTtf = File(fontsDir, config.ttfFileName)
            config.ttfFile.copyTo(destTtf, overwrite = true)
            if (config.boldTtfFile != null) {
                val destBoldTtf = File(fontsDir, config.ttfBoldFileName)
                config.boldTtfFile.copyTo(destBoldTtf, overwrite = true)
            }
            zipDirectory(extractDir, outputApk)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error building unsigned APK", e)
            false
        }
    }

    private fun signApk(context: Context, inputApk: File, outputApk: File) {
        val keyStore = KeyStore.getInstance("PKCS12")
        context.assets.open(KEYSTORE_FILE).use { keyInput ->
            keyStore.load(keyInput, KEYSTORE_PASSWORD.toCharArray())
        }
        val privateKey = keyStore.getKey(KEY_ALIAS, KEY_PASSWORD.toCharArray()) as PrivateKey
        val certificate = keyStore.getCertificate(KEY_ALIAS) as X509Certificate
        val signerConfig = ApkSigner.SignerConfig.Builder(
            KEY_ALIAS,
            privateKey,
            listOf(certificate)
        ).build()
        ApkSigner.Builder(listOf(signerConfig))
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .build()
            .sign()
    }

    private fun patchARSC(file: File, oldPlaceholder: String, newValue: String) {
        val bytes = file.readBytes()
        val u8Old = oldPlaceholder.toByteArray(Charsets.UTF_8)
        val u8New = newValue.toByteArray(Charsets.UTF_8)
        val u16Old = oldPlaceholder.toByteArray(Charsets.UTF_16LE)
        val u16New = newValue.toByteArray(Charsets.UTF_16LE)
        fun ByteArray.indexOfSub(arr: ByteArray): Int {
            if (arr.isEmpty() || arr.size > size) return -1
            outer@ for (i in 0..(size - arr.size)) {
                for (j in arr.indices) if (this[i + j] != arr[j]) continue@outer
                return i
            }
            return -1
        }
        fun replaceAt(idx: Int, patternLen: Int, repl: ByteArray): Boolean {
            if (repl.size > patternLen) return false
            System.arraycopy(repl, 0, bytes, idx, repl.size)
            for (i in idx + repl.size until idx + patternLen) bytes[i] = 0
            file.writeBytes(bytes)
            return true
        }
        val i8 = bytes.indexOfSub(u8Old)
        if (i8 >= 0) {
            if (!replaceAt(i8, u8Old.size, u8New)) throw IllegalArgumentException("new utf8 string longer than placeholder")
            return
        }
        val i16 = bytes.indexOfSub(u16Old)
        if (i16 >= 0) {
            if (!replaceAt(i16, u16Old.size, u16New)) throw IllegalArgumentException("new utf16 string longer than placeholder")
            return
        }
        Log.w(TAG, "placeholder not found in resources.arsc: $oldPlaceholder")
    }

    private fun patchXML(file: File, oldString: String, newString: String) {
        try {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buffer.getInt()
            if (magic != 0x00080003) {
                Log.e(TAG, "Not a binary XML file, magic: ${magic.toString(16)}")
                return
            }
            val fileSize = buffer.getInt()
            if (buffer.remaining() < 20) {
                Log.e(TAG, "Not enough data for string pool header")
                return
            }
            val chunkType = buffer.getInt()
            if (chunkType != 0x001C0001) {
                Log.e(TAG, "String pool not found, chunk type: ${chunkType.toString(16)}")
                return
            }
            val chunkSize = buffer.getInt()
            val stringCount = buffer.getInt()
            val styleCount = buffer.getInt()
            val flags = buffer.getInt()
            val stringsStart = buffer.getInt()
            val stylesStart = buffer.getInt()
            val isUtf8 = (flags and 0x00000100) != 0
            Log.d(TAG, "String pool: count=$stringCount, styles=$styleCount, utf8=$isUtf8, stringsStart=$stringsStart")
            if (buffer.remaining() < stringCount * 4) {
                Log.e(TAG, "Not enough data for string offsets")
                return
            }
            val stringOffsets = IntArray(stringCount) { buffer.getInt() }
            if (styleCount > 0 && buffer.remaining() >= styleCount * 4) {
                buffer.position(buffer.position() + styleCount * 4)
            }
            val chunkStart = 8
            val stringPoolDataStart = chunkStart + stringsStart
            Log.d(TAG, "String pool data starts at: $stringPoolDataStart")
            for (i in 0 until stringCount) {
                val stringPos = stringPoolDataStart + stringOffsets[i]
                if (stringPos >= bytes.size) {
                    Log.w(TAG, "String $i offset out of bounds: $stringPos")
                    continue
                }
                buffer.position(stringPos)
                if (buffer.remaining() < 2) {
                    Log.w(TAG, "Not enough data to read string $i")
                    continue
                }
                val currentString = try {
                    readString(buffer, isUtf8)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read string $i: ${e.message}")
                    continue
                }

                Log.d(TAG, "String $i: '$currentString'")
                if (currentString == oldString) {
                    Log.d(TAG, "Found target string at position $stringPos, replacing with: $newString")
                    if (newString.length > oldString.length) {
                        Log.e(TAG, "New string is longer than old string, cannot patch safely")
                        return
                    }
                    buffer.position(stringPos)
                    writeString(buffer, newString, bytes, isUtf8)
                    file.writeBytes(bytes)
                    Log.d(TAG, "patched manifest")
                    return
                }
            }
            Log.w(TAG, "Target string '$oldString' not found in manifest")
        } catch (e: Exception) {
            Log.e(TAG, "Error patching binary XML", e)
        }
    }
    
    private fun readString(buffer: ByteBuffer, isUtf8: Boolean): String {
        if (buffer.remaining() < 2) throw BufferUnderflowException()
        return if (isUtf8) {
            val len1 = buffer.get().toInt() and 0xFF
            val len2 = buffer.get().toInt() and 0xFF
            val length = if (len1 and 0x80 != 0) {
                ((len1 and 0x7F) shl 8) or len2
            } else {
                buffer.position(buffer.position() - 1)
                len1
            }
            if (buffer.remaining() < length) throw BufferUnderflowException()
            val strBytes = ByteArray(length)
            buffer.get(strBytes)
            if (buffer.hasRemaining() && buffer.get(buffer.position()) == 0.toByte()) {
                buffer.get()
            }
            String(strBytes, Charsets.UTF_8)
        } else {
            val length = buffer.getShort().toInt() and 0xFFFF
            if (length == 0) return ""
            if (buffer.remaining() < length * 2) throw BufferUnderflowException()
            val chars = CharArray(length)
            for (i in 0 until length) { chars[i] = buffer.getShort().toInt().toChar()}
            if (buffer.remaining() >= 2 && buffer.getShort(buffer.position()) == 0.toShort()) { buffer.getShort()}
            String(chars)
        }
    }

    private fun writeString(buffer: ByteBuffer, string: String, bytes: ByteArray, isUtf8: Boolean) {
        val pos = buffer.position()
        if (isUtf8) {
            val strBytes = string.toByteArray(Charsets.UTF_8)
            if (strBytes.size < 128) {
                bytes[pos] = strBytes.size.toByte()
                bytes[pos + 1] = strBytes.size.toByte()
                System.arraycopy(strBytes, 0, bytes, pos + 2, strBytes.size)
                bytes[pos + 2 + strBytes.size] = 0
            } else {
                val len = strBytes.size
                bytes[pos] = (0x80 or (len shr 8)).toByte()
                bytes[pos + 1] = (len and 0xFF).toByte()
                bytes[pos + 2] = len.toByte()
                System.arraycopy(strBytes, 0, bytes, pos + 3, strBytes.size)
                bytes[pos + 3 + strBytes.size] = 0
            }
        } else {
            val length = string.length
            bytes[pos] = (length and 0xFF).toByte()
            bytes[pos + 1] = (length shr 8).toByte()
            var offset = pos + 2
            for (char in string) {
                bytes[offset] = (char.code and 0xFF).toByte()
                bytes[offset + 1] = (char.code shr 8).toByte()
                offset += 2
            }
            bytes[offset] = 0
            bytes[offset + 1] = 0
        }
    }

    private fun createFontXml(file: File, config: FontConfig) { 
        val fontXml = if (config.boldTtfFile != null) {
            """<?xml version="1.0" encoding="utf-8"?>
<font displayname="${config.displayName}">
    <sans>
        <file>
            <filename>${config.ttfFileName}</filename>
            <droidname>DroidSans.ttf</droidname>
        </file>
        <file>
            <filename>${config.ttfBoldFileName}</filename>
            <droidname>DroidSans-Bold.ttf</droidname>
        </file>
    </sans>
</font>"""
        } else {
            """<?xml version="1.0" encoding="utf-8"?>
<font displayname="${config.displayName}">
    <sans>
        <file>
            <filename>${config.ttfFileName}</filename>
            <droidname>DroidSans.ttf</droidname>
        </file>
        <file>
            <filename>${config.ttfFileName}</filename>
            <droidname>DroidSans-Bold.ttf</droidname>
        </file>
    </sans>
</font>"""
        }
        file.writeText(fontXml)
    }

    private fun unzipApk(apkFile: File, destDir: File) {
        ZipInputStream(FileInputStream(apkFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
    
    private fun zipDirectory(sourceDir: File, outputZip: File) {
        ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                val relativePath = sourceDir.toPath().relativize(file.toPath()).toString().replace("\\", "/")
                if (relativePath.isEmpty()) return@forEach
                val zipEntry = ZipEntry(if (file.isDirectory) "$relativePath/" else relativePath)
                if (file.isFile) {
                    val bytes = file.readBytes()
                    zipEntry.method = ZipEntry.STORED
                    zipEntry.size = bytes.size.toLong()
                    zipEntry.compressedSize = bytes.size.toLong()
                    val crc = java.util.zip.CRC32()
                    crc.update(bytes)
                    zipEntry.crc = crc.value
                    zos.putNextEntry(zipEntry)
                    zos.write(bytes)
                } else {
                    zipEntry.method = ZipEntry.STORED
                    zipEntry.size = 0
                    zipEntry.compressedSize = 0
                    zipEntry.crc = 0
                    zos.putNextEntry(zipEntry)
                }
                zos.closeEntry()
            }
        }
    }
}