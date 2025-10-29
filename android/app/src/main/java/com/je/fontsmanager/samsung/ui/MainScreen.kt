package com.je.fontsmanager.samsung.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.je.fontsmanager.samsung.builder.FontBuilder
import com.je.fontsmanager.samsung.util.ShizukuAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import rikka.shizuku.Shizuku
import java.io.FileOutputStream
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import android.graphics.Typeface as AndroidTypefaceLegacy
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import android.util.TypedValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "home")
    object Settings : Screen("settings", "settings")
}

private fun sampleText(includeNumbers: Boolean = false): String =
    if (includeNumbers) "Cwm, fjord-bank glyphs vext quiz\n\n1234567890\n\n!@#$%^&*()_+~" else "The quick brown fox jumps over the lazy dog"

private fun makeSampleTextView(ctx: Context, textSizeSp: Float, textColor: Int, initialText: String = sampleText(false)) =
    TextView(ctx).apply {
        text = initialText
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        setTextColor(textColor)
    }

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                fun navTo(screen: Screen) {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true; restoreState = true
                    }
                }
                NavigationBarItem(icon = { Icon(Icons.Default.Home, null) }, label = { Text(Screen.Home.title) },
                    selected = currentRoute == Screen.Home.route, onClick = { navTo(Screen.Home) })
                NavigationBarItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text(Screen.Settings.title) },
                    selected = currentRoute == Screen.Settings.route, onClick = { navTo(Screen.Settings) })
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Home.route, modifier = Modifier.padding(innerPadding)) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFontFile by remember { mutableStateOf<File?>(null) }
    var selectedFontName by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showFontPreviewDialog by remember { mutableStateOf(false) }
    var previewTypeface by remember { mutableStateOf<AndroidTypefaceLegacy?>(null) }
    var showInstructionsDialog by remember { mutableStateOf(false) }

    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        scope.launch {
            isProcessing = false
            result?.let {
                if (it.resultCode == Activity.RESULT_OK) {
                    val pkgName = "com.monotype.android.font.${displayName.replace(Regex("[^a-zA-Z0-9]"), "")}"
                    val installed = FontInstallerUtils.isAppInstalled(context, pkgName)
                    Toast.makeText(context, if (installed) "Install succeeded" else "Install failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val ttfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val fileName = FontInstallerUtils.getFileName(context, it)
            val cachedFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.ttf")
            val success = try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    FileOutputStream(cachedFile).use { output -> input.copyTo(output) }
                }; true
            } catch (e: Exception) {
                Log.e("FontInstaller", "Failed to cache font", e); false
            }
            if (success) {
                selectedFontFile = cachedFile
                selectedFontName = fileName
                displayName = fileName.removeSuffix(".ttf")
                // Toast.makeText(context, "Font file selected", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(context, "Failed to save font file", Toast.LENGTH_SHORT).show()
        }
    }

    fun performInstall() {
        scope.launch {
            isProcessing = true
            FontInstallerUtils.buildAndInstallFont(
                context,
                selectedFontFile!!,
                displayName,
                installLauncher,
                onAlreadyInstalled = {
                    isProcessing = false
                    Toast.makeText(context, "Font already installed. Uninstall it first.", Toast.LENGTH_LONG).show()
                },
                onComplete = { success ->
                    isProcessing = false
                    val pkgName = "com.monotype.android.font.${displayName.replace(Regex("[^a-zA-Z0-9]"), "")}"
                    val installed = FontInstallerUtils.isAppInstalled(context, pkgName)
                    Toast.makeText(context, if (installed && success) "Install succeeded" else "Install failed", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Customize font name") },
            text = {
                Column { Text("Enter display name for the font:"); Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display name") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { showNameDialog = false; performInstall() }, enabled = displayName.isNotBlank()) { Text("Install") }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text("Cancel") } }
        )
    }

    val appIconBitmap: Bitmap? = remember {
        val d = context.packageManager.getApplicationIcon(context.applicationInfo)
        when (d) {
            is BitmapDrawable -> d.bitmap
            else -> {
                val w = d.intrinsicWidth.takeIf { it > 0 } ?: 128
                val h = d.intrinsicHeight.takeIf { it > 0 } ?: 128
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                    android.graphics.Canvas(bmp).apply { d.setBounds(0, 0, w, h); d.draw(this) }
                }
            }
        }
    }

    LaunchedEffect(selectedFontFile) {
        selectedFontFile?.let { file ->
            try {
                previewTypeface = AndroidTypefaceLegacy.createFromFile(file)
            } catch (_: Exception) {
                previewTypeface = null
            }
        } ?: run { previewTypeface = null }
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Box(Modifier.fillMaxSize()) {
        val configuration = LocalConfiguration.current
        val screenHeightPx = configuration.screenHeightDp.dp
        var contentHeightPx by remember { mutableStateOf(0.dp) }
        val scrollState = rememberScrollState()
        val density = LocalDensity.current

        Column(
            Modifier.fillMaxSize().let {
                if (contentHeightPx > screenHeightPx) it.verticalScroll(scrollState, true) else it
            }.padding(24.dp).onGloballyPositioned {
                contentHeightPx = with(density) { it.size.height.toDp() }
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (appIconBitmap != null) Icon(painter = BitmapPainter(appIconBitmap.asImageBitmap()), contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Unspecified)
            Spacer(Modifier.height(16.dp))
            Text("Font installer", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(32.dp))

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (selectedFontName != null) {
                        Text("Selected font", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Column(Modifier.fillMaxWidth().clickable { showFontPreviewDialog = true }.padding(vertical = 8.dp)) {
                            Text(selectedFontName!!, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(8.dp))
                            if (previewTypeface != null)
                                AndroidView(factory = { ctx -> makeSampleTextView(ctx, 14f, onSurfaceColor) },
                                    update = { tv -> tv.typeface = previewTypeface; tv.text = sampleText(false); tv.setTextColor(onSurfaceColor) },
                                    modifier = Modifier.fillMaxWidth())
                            else Text(sampleText(false), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Display name: $displayName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else Text("No font file selected", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (showFontPreviewDialog && previewTypeface != null)
                Dialog(onDismissRequest = { showFontPreviewDialog = false }) {
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Font Preview", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(16.dp))
                            AndroidView(factory = { ctx -> makeSampleTextView(ctx, 20f, onSurfaceColor, sampleText(true)) },
                                update = { tv -> tv.typeface = previewTypeface; tv.text = sampleText(true); tv.setTextColor(onSurfaceColor) },
                                modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { showFontPreviewDialog = false }) { Text("Close") }
                        }
                    }
                }

            Spacer(Modifier.height(16.dp))
            Button(onClick = { ttfPicker.launch(arrayOf("font/ttf", "font/*", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth(), enabled = !isProcessing) {
                Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Select font (.ttf)")
            }

            Spacer(Modifier.height(12.dp))
            if (selectedFontFile != null) {
                OutlinedButton(onClick = { showNameDialog = true }, modifier = Modifier.fillMaxWidth(), enabled = !isProcessing) {
                    Icon(Icons.Default.Edit, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Display name")
                }
                Spacer(Modifier.height(12.dp))
            }

            FilledTonalButton(onClick = { if (displayName.isBlank()) showNameDialog = true else performInstall() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing && selectedFontFile != null
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Building")
                } else {
                    Icon(Icons.Default.Build, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Build and install")
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = {
                val intent = Intent("com.samsung.settings.FontStyleActivity")
                try { context.startActivity(intent) } catch (e: Exception) { Toast.makeText(context, "Cannot open font settings", Toast.LENGTH_SHORT).show() }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Open font settings")
            }

            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick = { showInstructionsDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Info, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Instructions")
            }
        }

        if (showInstructionsDialog)
            Dialog(onDismissRequest = { showInstructionsDialog = false }) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Instructions", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.height(8.dp))
                    Text("1. Select a font\n2. Customize display name (optional)\n3. Build & install\n4. Apply in Samsung settings\n\nNote: if certain fonts don't apply, uninstall, restart your device then retry", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showInstructionsDialog = false }) { Text("Close") }
                }
            }
        }
    }

}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installedFonts by remember { mutableStateOf<List<String>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var shizukuAvailable by remember { mutableStateOf(ShizukuAPI.isUsable()) }

    val filteredFonts = installedFonts.filterNot {
        it.endsWith(".foundation") || it.endsWith(".samsungone") || it.endsWith(".roboto")
    }
    fun refreshFonts() {
        scope.launch {
            isRefreshing = true
            installedFonts = FontInstallerUtils.getInstalledCustomFonts(context)
            isRefreshing = false
        }
    }
    val uninstallLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshFonts() }
    LaunchedEffect(Unit) { refreshFonts() }
    val fontTypefaces = remember { mutableStateMapOf<String, AndroidTypefaceLegacy?>() }
    LaunchedEffect(installedFonts) {
        installedFonts.forEach { pkg ->
            if (!fontTypefaces.containsKey(pkg)) {
                try {
                    val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
                    val apkFile = File(appInfo.sourceDir)
                    val ttfFile = FontInstallerUtils.extractFontPreview(apkFile, context.cacheDir)
                    val tf = ttfFile?.let { AndroidTypefaceLegacy.createFromFile(it) }
                    fontTypefaces[pkg] = tf
                } catch (_: Exception) {
                    fontTypefaces[pkg] = null
                }
            }
        }
    }
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 16.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                Text("Font installer v1.1", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Installed fonts", style = MaterialTheme.typography.titleMedium)
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = { refreshFonts() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (filteredFonts.isNotEmpty()) {
                    filteredFonts.forEach { pkg ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        pkg.substringAfterLast(".").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val tf = fontTypefaces[pkg]
                                    if (tf != null) {
                                        Spacer(Modifier.height(4.dp))
                                        AndroidView(
                                            factory = { ctx ->
                                                makeSampleTextView(ctx, 14f, onSurfaceColor)
                                            },
                                            update = { tv ->
                                                tv.typeface = tf
                                                tv.text = sampleText(false)
                                                tv.setTextColor(onSurfaceColor)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        val success = if (ShizukuAPI.shouldUseShizuku(context)) {
                                            ShizukuAPI.uninstall(pkg)
                                        } else {
                                            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply { data = Uri.parse("package:$pkg") }
                                            uninstallLauncher.launch(intent)
                                            null
                                        }
                                        success?.let {
                                            Toast.makeText(context, if (it) "Uninstalled" else "Uninstall failed", Toast.LENGTH_SHORT).show()
                                            if (it) refreshFonts()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Uninstall", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "No custom fonts installed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Shizuku", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            val shizukuInstalled = remember { ShizukuAPI.isInstalled() }
            var shizukuAuthorized by remember { mutableStateOf(false) }
            var shizukuRunning by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(shizukuInstalled) {
                val running = try { Shizuku.pingBinder() } catch (_: Exception) { false }
                shizukuRunning = running
                shizukuAuthorized = if (running) ShizukuAPI.hasPermission() else false
            }
            val shizukuStatus = when {
                !shizukuInstalled -> "Shizuku is not installed"
                shizukuRunning && shizukuAuthorized -> "Running (authorized)"
                shizukuRunning -> "Running (unauthorized)"
                else -> "Not running"
            }
            Text("Status: $shizukuStatus", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (shizukuInstalled && shizukuRunning && !shizukuAuthorized) {
                    Button(onClick = {
                    ShizukuAPI.requestPermission(
                    onGranted = {
                    shizukuAuthorized = true
                    Toast.makeText(context, "Permission granted", Toast.LENGTH_SHORT).show()},
                    onDenied = {
                    shizukuAuthorized = false
                    Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()})})
                    { Text("Request permission")}}
                }
            }
        }
    }
}

object FontInstallerUtils {
    private const val TAG = "FontInstaller"
    private const val FONT_PACKAGE_PREFIX = "com.monotype.android.font"

    fun getFileName(context: Context, uri: Uri): String =
        when (uri.scheme) {
            "content" -> context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).coerceAtLeast(0)) ?: "Unknown" else "Unknown"
            } ?: "Unknown"
            else -> File(uri.path ?: "").name
        }

    fun getInstalledCustomFonts(context: Context): List<String> = try {
        context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName.startsWith(FONT_PACKAGE_PREFIX) }
            .map { it.packageName }
    } catch (e: Exception) { Log.e(TAG, "Failed to get installed fonts", e); emptyList() }

    suspend fun buildAndInstallFont(
        context: Context,
        ttfFile: File,
        displayName: String,
        installLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
        onAlreadyInstalled: () -> Unit,
        onComplete: (Boolean) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            val fontName = displayName.replace(Regex("[^a-zA-Z0-9]"), "")
            val config = FontBuilder.FontConfig(displayName = displayName, fontName = fontName, ttfFile = ttfFile)
            if (isAppInstalled(context, config.packageName)) {
                withContext(Dispatchers.Main) { onAlreadyInstalled() }
                return@withContext
            }

            val outputApk = File(context.cacheDir, "signed_${System.currentTimeMillis()}.apk")
            if (!FontBuilder.buildAndSignFontApk(context, config, outputApk)) {
                Log.e(TAG, "buildAndSignFontApk failed")
                withContext(Dispatchers.Main) { onComplete(false) }
                return@withContext
            }
            if (ShizukuAPI.isUsable()) {
                val success = ShizukuAPI.installApk(outputApk) { fallbackApk ->
                    ShizukuAPI.fallbackInstall(context, fallbackApk)
                }
                delay(500)
                outputApk.delete()
                withContext(Dispatchers.Main) { onComplete(success) }
            } else {
                installApk(context, outputApk, installLauncher)
                delay(2000)
                outputApk.delete()
                withContext(Dispatchers.Main) { onComplete(true) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error installing font", e)
            withContext(Dispatchers.Main) { onComplete(false) }
        }
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun installApk(context: Context, apkFile: File, installLauncher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        if (!apkFile.exists() || apkFile.length() == 0L) return
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        installLauncher.launch(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = apkUri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
        })
    }

    fun extractFontPreview(apkFile: File, cacheDir: File): File? {
        return try {
            val zip = java.util.zip.ZipFile(apkFile)
            val entry = zip.entries().asSequence().firstOrNull { it.name.endsWith(".ttf", ignoreCase = true) }
            if (entry != null) {
                val outFile = File(cacheDir, "font_preview_${apkFile.nameWithoutExtension}_${System.currentTimeMillis()}.ttf")
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                outFile
            } else null
        } catch (_: Exception) {
            null
        }
    }
}