package com.je.fontsmanager.samsung.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import rikka.shizuku.Shizuku
import java.io.File
import androidx.core.content.edit

object ShizukuAPI {
    private const val TAG = "ShizukuAPI"
    private const val PREFS_NAME = "shizuku_prefs"
    private const val KEY_PERMISSION_DENIED = "permission_denied"
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun context() = appContext ?: throw IllegalStateException("Call init() first")

    fun shouldUseShizuku(context: Context): Boolean {
        if (isUsable()) {
            setPermissionDenied(false)
            return true
        }
        return false
    }

    private fun isPermissionDenied(): Boolean {
        val prefs = context().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PERMISSION_DENIED, false)
    }

    private fun setPermissionDenied(denied: Boolean) {
        val prefs = context().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_PERMISSION_DENIED, denied) }
    }

    fun isInstalled(): Boolean = try {
        context().packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) { false }

    fun isRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun hasPermission(): Boolean = try { 
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED 
    } catch (_: Throwable) { false }

    fun isUsable(): Boolean = isInstalled() && isRunning() && hasPermission()

    fun requestPermission(
        requestCode: Int = 1001,
        onGranted: (() -> Unit)? = null,
        onDenied: (() -> Unit)? = null
    ) {
        if (!isInstalled()) {
            setPermissionDenied(true)
            onDenied?.invoke()
            return
        }
        if (!isRunning()) {
            setPermissionDenied(true)
            onDenied?.invoke()
            return
        }
        if (hasPermission()) {
            setPermissionDenied(false)
            onGranted?.invoke()
            return
        }
        if (isPermissionDenied()) {
            onDenied?.invoke()
            return
        }
        Shizuku.requestPermission(requestCode)
        Shizuku.addRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                setPermissionDenied(false)
                onGranted?.invoke()
            } else {
                setPermissionDenied(true)
                onDenied?.invoke()
            }
        }
    }

    // under no circumstances should this function be altered or use any other API
    fun installApk(apkFile: File, fallback: (File) -> Unit): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) return false
        return try {
            if (isUsable()) {
                val staging = File("/sdcard/Download", apkFile.name)
                apkFile.copyTo(staging, overwrite = true)
                val tmp = "/data/local/tmp/${staging.name}"
                exec("cp \"${staging.absolutePath}\" \"$tmp\" && chmod 777 \"$tmp\"")
                val sessionOutput = exec("pm install-create -r -i ${context().packageName}")
                val sessionId = "\\[(\\d+)]".toRegex().find(sessionOutput)?.groupValues?.get(1) ?: return false
                val writeResult = exec("pm install-write $sessionId base \"$tmp\"")
                if (writeResult.contains("Error", true) || writeResult.contains("Unable", true)) {
                    exec("rm \"$tmp\"")
                    return false
                }
                val commitResult = exec("pm install-commit $sessionId")
                exec("rm \"$tmp\""); staging.delete()
                commitResult.contains("Success", true)
            } else {
                fallback(apkFile)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            false
        }
    }

    fun uninstall(packageName: String, fallback: (String) -> Unit = {}): Boolean {
        return try {
            if (isUsable()) {
                val result = exec("pm uninstall $packageName")
                result.contains("Success", true)
            } else {
                fallback(packageName)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall failed", e)
            false
        }
    }

    private fun exec(command: String): String {
        val args = arrayOf("sh", "-c", command)
        val process: Process = try {
            Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                .apply { isAccessible = true }
                .invoke(null, args, null, null) as Process
        } catch (_: Exception) { Runtime.getRuntime().exec(args) }

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val error = process.errorStream.bufferedReader().use { it.readText() }
        process.waitFor()
        return output.ifEmpty { error }
    }

    fun fallbackInstall(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
        }
        context.startActivity(intent)
    }

    fun fallbackUninstall(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply { 
            data = Uri.parse("package:$packageName") 
        }
        context.startActivity(intent)
    }
}
