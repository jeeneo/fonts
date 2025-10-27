package com.je.fontsmanager.samsung.util

import android.content.Context
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.File

object ShizukuAPI {
    private const val TAG = "ShizukuAPI"
    private const val PREFS_NAME = "shizuku_prefs"
    private const val KEY_PERMISSION_DENIED = "permission_denied"
    private var context: Context? = null
    fun init(ctx: Context) {
        context = ctx.applicationContext
    }
    private fun getPrefs() = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    public var permissionDenied: Boolean
        get() = getPrefs()?.getBoolean(KEY_PERMISSION_DENIED, false) ?: false
        set(value) { getPrefs()?.edit()?.putBoolean(KEY_PERMISSION_DENIED, value)?.apply() }

    fun isUsable(): Boolean = try {
        Shizuku.pingBinder() && checkPermission()
    } catch (e: Exception) {
        Log.e(TAG, "Shizuku not usable", e)
        false
    }
    fun shouldUseShizuku(): Boolean = !permissionDenied && isUsable()
    fun shouldRequestOnStartup(): Boolean = !permissionDenied
    fun recheckPermission(): Boolean {
        val usable = isUsable()
        if (usable) permissionDenied = false
        return usable
    }
    private fun checkPermission(): Boolean {
        val hasPermission = !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission && !permissionDenied) {
            permissionDenied = true
            Log.d(TAG, "Permission denied, falling back to normal install")
        }
        return hasPermission
    }
    fun requestPermission(requestCode: Int = 1001) {
        if (Shizuku.isPreV11() || checkPermission()) return
        Shizuku.requestPermission(requestCode)
    }
    fun install(context: Context, apkFile: File): Boolean {
        if (!isUsable() || !apkFile.exists() || apkFile.length() == 0L) return false
        val stagingFile = File("/sdcard/Download", apkFile.name)
        try {
            apkFile.copyTo(stagingFile, overwrite = true)
        } catch (e: Exception) {
            Log.e("ShizukuAPI", "Failed to copy APK to staging location", e)
            return false
        }
        val tmpPath = "/data/local/tmp/${stagingFile.name}"
        executeShizukuCommand("cp \"${stagingFile.absolutePath}\" \"$tmpPath\" && chmod 777 \"$tmpPath\"")
        Thread.sleep(50)
        val sessionOutput = executeShizukuCommand("pm install-create -r -i ${context.packageName}")
        val sessionId = "\\[(\\d+)]".toRegex().find(sessionOutput)?.groupValues?.get(1) ?: return false
        val writeResult = executeShizukuCommand("pm install-write $sessionId base \"$tmpPath\"")
        if (writeResult.contains("Error", ignoreCase = true) || writeResult.contains("Unable", ignoreCase = true)) {
            executeShizukuCommand("rm \"$tmpPath\"")
            return false
        }
        val commitResult = executeShizukuCommand("pm install-commit $sessionId")
        executeShizukuCommand("rm \"$tmpPath\"")
        stagingFile.delete()
        return commitResult.contains("Success", ignoreCase = true)
    }
    fun uninstall(packageName: String): Boolean {
        if (!isUsable()) {
            Log.e(TAG, "Shizuku is not usable")
            return false
        }
        return try {
            val result = executeShizukuCommand("pm uninstall $packageName")
            val success = result.contains("Success", ignoreCase = true)
            Log.d(TAG, if (success) "Uninstalled $packageName" else "Uninstall failed for $packageName: $result")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to uninstall via Shizuku", e)
            false
        }
    }
    private fun executeShizukuCommand(command: String): String = try {
        Log.d(TAG, "Executing: $command")
        val args = arrayOf("sh", "-c", command)
        val process: Process = try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }.invoke(null, args, null, null) as Process
        } catch (e: Exception) {
            Log.w(TAG, "Reflection failed, falling back to Runtime.exec", e)
            Runtime.getRuntime().exec(args)
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val error = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        Log.d(TAG, "Exit code: $exitCode")
        if (output.isNotEmpty()) Log.d(TAG, "Output: $output")
        if (error.isNotEmpty()) Log.w(TAG, "Error: $error")
        output.ifEmpty { error }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to execute: $command", e)
        throw e
    }
}