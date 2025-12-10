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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.je.fontsmanager.samsung.util.CacheCleanupUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import rikka.shizuku.Shizuku
import java.io.FileOutputStream
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.graphics.createBitmap
import com.je.fontsmanager.samsung.R

sealed class Screen(val route: String, val titleRes: Int) {
    object Home : Screen("home", R.string.nav_home)
    object Settings : Screen("settings", R.string.title_manage)
    object FontPreview : Screen("font_preview", R.string.dialog_font_preview_title)
}

private fun getRandomSimpleText(context: Context): String {
    val simpleTexts = mutableListOf<String>()
    val locale = context.resources.configuration.locales.get(0)
    val resIds = mutableListOf(
        R.string.sample_text_simple_1,
        R.string.sample_text_simple_2,
    )
    if (locale.language == "en") {
        resIds.add(R.string.sample_text_simple_3)
    }
    resIds.forEach { resId ->
        try {
            val text = context.getString(resId)
            if (text.isNotEmpty()) {
                simpleTexts.add(text)
            }
        } catch (_: Exception) {}
    }
    return simpleTexts.randomOrNull() ?: "sample text"
}

@Composable
private fun sampleText(includeNumbers: Boolean = false): String {
    val context = LocalContext.current
    return if (includeNumbers) {
        androidx.compose.ui.res.stringResource(R.string.sample_text_complex)
    } else {
        remember { getRandomSimpleText(context) }
    }
}

private fun makeSampleTextView(ctx: Context, textSizeSp: Float, textColor: Int, initialText: String) =
    TextView(ctx).apply {
        text = initialText
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        setTextColor(textColor)
    }

class HomeScreenState {
    var selectedFontFile by mutableStateOf<File?>(null)
    var selectedFontName by mutableStateOf<String?>(null)
    var selectedBoldFontFile by mutableStateOf<File?>(null)
    var selectedBoldFontName by mutableStateOf<String?>(null)
    var displayName by mutableStateOf("")
    var previewTypeface by mutableStateOf<AndroidTypefaceLegacy?>(null)
    var boldPreviewTypeface by mutableStateOf<AndroidTypefaceLegacy?>(null)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val homeScreenState = remember { HomeScreenState() }
    Scaffold(
        bottomBar = {
            if (currentRoute != Screen.FontPreview.route) {
                NavigationBar {
                    fun navTo(screen: Screen) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    }
                    NavigationBarItem(icon = { Icon(Icons.Default.Home, null) }, label = { Text(androidx.compose.ui.res.stringResource(Screen.Home.titleRes)) },
                        selected = currentRoute == Screen.Home.route, onClick = { navTo(Screen.Home) })
                    NavigationBarItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text(androidx.compose.ui.res.stringResource(Screen.Settings.titleRes)) },
                        selected = currentRoute == Screen.Settings.route, onClick = { navTo(Screen.Settings) })
                }
            }
        }
    ) { innerPadding ->
        val navHostPadding = if (currentRoute == Screen.FontPreview.route) {
            PaddingValues(0.dp)
        } else {
            innerPadding
        }
        NavHost(navController, startDestination = Screen.Home.route, modifier = Modifier.padding(navHostPadding)) {
            composable(Screen.Home.route) { HomeScreen(navController, homeScreenState) }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.FontPreview.route) { FontPreviewScreen(navController, homeScreenState) }
        }
    }
}

@Composable
fun HomeScreen(navController: androidx.navigation.NavController, sharedState: HomeScreenState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFontFile by sharedState::selectedFontFile
    var selectedFontName by sharedState::selectedFontName
    var selectedBoldFontFile by sharedState::selectedBoldFontFile
    var selectedBoldFontName by sharedState::selectedBoldFontName
    var displayName by sharedState::displayName
    var previewTypeface by sharedState::previewTypeface
    var boldPreviewTypeface by sharedState::boldPreviewTypeface
    
    var isProcessing by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showInstructionsDialog by remember { mutableStateOf(false) }
    var pendingInstallPackage by remember { mutableStateOf<String?>(null) }
    var awaitingInstallResult by remember { mutableStateOf(false) }
    var showFontDropdown by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val excludeFiles = listOfNotNull(selectedFontFile, selectedBoldFontFile)
                CacheCleanupUtils.cleanup(context.cacheDir, excludeFiles)
                if (awaitingInstallResult) {
                    val pkgName = pendingInstallPackage
                    if (pkgName != null) {
                        scope.launch {
                            delay(300)
                            val installed = FontInstallerUtils.isAppInstalled(context, pkgName)
                            if (installed) {
                                isProcessing = false
                                awaitingInstallResult = false
                                Toast.makeText(context, context.getString(R.string.toast_install_succeeded), Toast.LENGTH_SHORT).show()
                                pendingInstallPackage = null
                                CacheCleanupUtils.cleanup(context.cacheDir)
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        scope.launch {
            val pkgName = pendingInstallPackage
            if (pkgName != null) {
                delay(300)
                val installed = FontInstallerUtils.isAppInstalled(context, pkgName)
                isProcessing = false
                awaitingInstallResult = false
                if (installed) {
                    Toast.makeText(context, context.getString(R.string.toast_install_succeeded), Toast.LENGTH_SHORT).show()
                } else {
                    if (result.resultCode == Activity.RESULT_CANCELED) {
                        Toast.makeText(context, context.getString(R.string.toast_install_cancelled), Toast.LENGTH_SHORT).show()
                    }
                }
                pendingInstallPackage = null
            } else {
                isProcessing = false
                awaitingInstallResult = false
            }
        }
    }

    val ttfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val fileName = FontInstallerUtils.getFileName(context, it)
            if (!fileName.endsWith(".ttf", ignoreCase = true)) {
                Toast.makeText(context, context.getString(R.string.toast_only_ttf_supported), Toast.LENGTH_SHORT).show()
                return@let
            }
            try { selectedFontFile?.delete() } catch (_: Exception) {}
            previewTypeface = null
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
                try {
                    previewTypeface = AndroidTypefaceLegacy.createFromFile(cachedFile)
                } catch (e: Exception) {
                    Log.e("FontInstaller", "Failed to load font preview", e)
                    previewTypeface = null
                }
            } else Toast.makeText(context, context.getString(R.string.toast_failed_to_save), Toast.LENGTH_SHORT).show()
        }
    }

    val boldTtfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val fileName = FontInstallerUtils.getFileName(context, it)
            if (!fileName.endsWith(".ttf", ignoreCase = true)) {
                Toast.makeText(context, context.getString(R.string.toast_only_ttf_supported), Toast.LENGTH_SHORT).show()
                return@let
            }
            try { selectedBoldFontFile?.delete() } catch (_: Exception) {}
            val cachedFile = File(context.cacheDir, "temp_bold_${System.currentTimeMillis()}.ttf")
            val success = try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    FileOutputStream(cachedFile).use { output -> input.copyTo(output) }
                }; true
            } catch (e: Exception) {
                Log.e("FontInstaller", "Failed to cache bold font", e); false
            }
            if (success) {
                selectedBoldFontFile = cachedFile
                selectedBoldFontName = fileName
                try {
                    boldPreviewTypeface = AndroidTypefaceLegacy.createFromFile(cachedFile)
                } catch (e: Exception) {
                    Log.e("FontInstaller", "Failed to load bold font preview", e)
                    boldPreviewTypeface = null
                }
            } else Toast.makeText(context, context.getString(R.string.toast_failed_to_save_bold), Toast.LENGTH_SHORT).show()
        }
    }

    fun performInstall() {
        val pkgName = "com.monotype.android.font.${displayName.replace(Regex("[^a-zA-Z0-9]"), "")}"
        pendingInstallPackage = pkgName; awaitingInstallResult = true
        scope.launch {
            isProcessing = true
            FontInstallerUtils.buildAndInstallFont(context, selectedFontFile!!, displayName, installLauncher,
                onAlreadyInstalled = {
                    isProcessing = false; awaitingInstallResult = false; pendingInstallPackage = null
                    Toast.makeText(context, context.getString(R.string.toast_already_installed), Toast.LENGTH_LONG).show()
                    CacheCleanupUtils.cleanup(context.cacheDir)
                },
                onComplete = { success ->
                    if (ShizukuAPI.isUsable()) {
                        isProcessing = false; awaitingInstallResult = false
                        Toast.makeText(context, context.getString(if (FontInstallerUtils.isAppInstalled(context, pkgName) && success) R.string.toast_install_succeeded else R.string.toast_install_failed), Toast.LENGTH_SHORT).show()
                        pendingInstallPackage = null; CacheCleanupUtils.cleanup(context.cacheDir)
                    }
                },
                boldTtfFile = selectedBoldFontFile
            )
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(androidx.compose.ui.res.stringResource(R.string.dialog_customize_name_title)) },
            text = {
                Column {
                    Text(androidx.compose.ui.res.stringResource(R.string.dialog_customize_name_message))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(displayName, { if (it.length <= 50) displayName = it },
                        label = { Text(androidx.compose.ui.res.stringResource(R.string.dialog_customize_name_hint)) },
                        singleLine = true, isError = displayName.length > 50,
                        supportingText = { Text(androidx.compose.ui.res.stringResource(R.string.dialog_customize_name_counter, displayName.length)) })
                    if (displayName.length > 50) Text(androidx.compose.ui.res.stringResource(R.string.dialog_customize_name_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton({ showNameDialog = false; performInstall() }, enabled = displayName.isNotBlank() && displayName.length <= 50) { Text(androidx.compose.ui.res.stringResource(R.string.button_install)) } },
            dismissButton = { TextButton({ showNameDialog = false }) { Text(androidx.compose.ui.res.stringResource(R.string.button_cancel)) } }
        )
    }

    val appIconBitmap: Bitmap? = remember {
        val d = context.packageManager.getApplicationIcon(context.applicationInfo)
        when (d) {
            is BitmapDrawable -> d.bitmap
            else -> {
                val w = d.intrinsicWidth.takeIf { it > 0 } ?: 128
                val h = d.intrinsicHeight.takeIf { it > 0 } ?: 128
                createBitmap(w, h).also { bmp ->
                    android.graphics.Canvas(bmp).apply { d.setBounds(0, 0, w, h); d.draw(this) }
                }
            }
        }
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            appIconBitmap?.let {
                Icon(BitmapPainter(it.asImageBitmap()), null, Modifier.size(48.dp), tint = Color.Unspecified)
            }
            Spacer(Modifier.height(16.dp))
            Text(androidx.compose.ui.res.stringResource(R.string.title_font_installer), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(32.dp))

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (selectedFontName != null) {
                        Text(androidx.compose.ui.res.stringResource(R.string.label_selected_font), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                navController.navigate(Screen.FontPreview.route)
                                Log.d("FontInstaller", "Navigating to font preview, typeface is ${if (previewTypeface != null) "loaded" else "null"}")
                            }.padding(vertical = 8.dp)
                        ) {
                            Text(selectedFontName!!, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(8.dp))
                            if (previewTypeface != null) {
                                val previewText = sampleText(false)
                                AndroidView(
                                    factory = { ctx -> makeSampleTextView(ctx, 14f, onSurfaceColor, previewText).also { Log.d("FontInstaller", "Creating preview TextView with typeface") } },
                                    update = { tv -> tv.typeface = previewTypeface; tv.text = previewText; tv.setTextColor(onSurfaceColor) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else Text(sampleText(false), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(androidx.compose.ui.res.stringResource(R.string.label_display_name, displayName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (selectedBoldFontFile != null) {
                            Spacer(Modifier.height(12.dp))
                            Divider()
                            Spacer(Modifier.height(12.dp))
                            Text(androidx.compose.ui.res.stringResource(R.string.label_bold_variant), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(selectedBoldFontName!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else Text(androidx.compose.ui.res.stringResource(R.string.label_no_font_selected), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    { showFontDropdown = true },
                    Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.button_select_font))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }

                DropdownMenu(showFontDropdown, { showFontDropdown = false }) {
                    DropdownMenuItem(
                        { Text(androidx.compose.ui.res.stringResource(R.string.menu_regular_font)) },
                        {
                            showFontDropdown = false
                            ttfPicker.launch(arrayOf("font/ttf", "font/*", "application/octet-stream"))
                        },
                        leadingIcon = { Icon(Icons.Default.Add, null) }
                    )
                    DropdownMenuItem(
                        { Text(androidx.compose.ui.res.stringResource(R.string.menu_bold_variant)) },
                        {
                            showFontDropdown = false
                            boldTtfPicker.launch(arrayOf("font/ttf", "font/*", "application/octet-stream"))
                        },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                        enabled = selectedFontFile != null
                    )
                    if (selectedBoldFontFile != null) {
                        DropdownMenuItem(
                            { Text(androidx.compose.ui.res.stringResource(R.string.menu_remove_bold)) },
                            {
                                showFontDropdown = false
                                try { selectedBoldFontFile?.delete() } catch (_: Exception) {}
                                selectedBoldFontFile = null
                                selectedBoldFontName = null
                                boldPreviewTypeface = null
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (selectedFontFile != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ showNameDialog = true }, Modifier.weight(1f), enabled = !isProcessing) {
                        Icon(Icons.Default.Edit, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(androidx.compose.ui.res.stringResource(R.string.button_display_name))
                    }
                    FilledTonalButton(
                        { if (displayName.isBlank()) showNameDialog = true else performInstall() },
                        Modifier.weight(1f),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(androidx.compose.ui.res.stringResource(R.string.button_building))
                        } else {
                            Icon(Icons.Default.Build, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(androidx.compose.ui.res.stringResource(R.string.button_build))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton({
                try { context.startActivity(Intent("com.samsung.settings.FontStyleActivity")) }
                catch (e: Exception) { Toast.makeText(context, context.getString(R.string.toast_cannot_open_settings), Toast.LENGTH_SHORT).show() }
            }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(androidx.compose.ui.res.stringResource(R.string.button_open_font_settings))
            }
        }

        if (showInstructionsDialog)
            Dialog({ showInstructionsDialog = false }) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(androidx.compose.ui.res.stringResource(R.string.dialog_instructions_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.height(8.dp))
                        Text(androidx.compose.ui.res.stringResource(R.string.dialog_instructions_content), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.height(16.dp))
                        Button({ showInstructionsDialog = false }) { Text(androidx.compose.ui.res.stringResource(R.string.button_close)) }
                    }
                }
            }

        IconButton(
            onClick = { showInstructionsDialog = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.content_description_instructions),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontPreviewScreen(navController: androidx.navigation.NavController, sharedState: HomeScreenState) {
    val previewTypeface = sharedState.previewTypeface
    val fontName = sharedState.selectedFontName
    val boldTypeface = sharedState.boldPreviewTypeface
    val boldFontName = sharedState.selectedBoldFontName

    var selectedStyle by remember { mutableStateOf(PreviewStyle.Regular) }
    var customText by remember { mutableStateOf("") }
    var textSize by remember { mutableStateOf(24f) }

    val context = LocalContext.current
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val defaultSampleText = remember { getRandomSimpleText(context) }
    val simpleSampleText = remember { getRandomSimpleText(context) }

    // Helper to derive the proper typeface for the selected style
    fun deriveTypeface(
        base: AndroidTypefaceLegacy?,
        bold: AndroidTypefaceLegacy?
    ): AndroidTypefaceLegacy? {
        if (base == null) return null
        // Prefer provided bold typeface for bold styles
        val useTf = if (selectedStyle.prefersBoldTf && bold != null) bold else base
        // On API 28+, Typeface.create(Typeface, weight, italic) exists; we call it via reflection-safe signature
        return try {
            AndroidTypefaceLegacy.create(useTf, selectedStyle.weight, selectedStyle.italic)
        } catch (_: Throwable) {
            // Fallback to older style mapping
            val styleConst = when {
                selectedStyle.weight >= 700 && selectedStyle.italic -> android.graphics.Typeface.BOLD_ITALIC
                selectedStyle.weight >= 700 -> android.graphics.Typeface.BOLD
                selectedStyle.italic -> android.graphics.Typeface.ITALIC
                else -> android.graphics.Typeface.NORMAL
            }
            AndroidTypefaceLegacy.create(useTf, styleConst)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fontName ?: androidx.compose.ui.res.stringResource(R.string.dialog_font_preview_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(R.string.button_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (previewTypeface == null) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.error_font_preview),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.preview_text_size, textSize.toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = textSize,
                            onValueChange = { textSize = it },
                            valueRange = 12f..72f,
                            steps = 11
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.preview_custom_text_hint)) },
                    placeholder = { Text(simpleSampleText) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Spacer(Modifier.height(16.dp))

                // Style selector (segmented buttons)
                Text("Style", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val items = PreviewStyle.entries
                    items.forEachIndexed { index, style ->
                        SegmentedButton(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                            shape = SegmentedButtonDefaults.itemShape(index, items.size),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text(
                                style.label,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                val currentTypeface = deriveTypeface(previewTypeface, boldTypeface)
                Spacer(Modifier.height(16.dp))

                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        val header = when (selectedStyle) {
                            PreviewStyle.Regular -> stringResource(R.string.label_regular_variant)
                            PreviewStyle.Italic -> stringResource(R.string.label_italic_variant)
                            PreviewStyle.Medium -> stringResource(R.string.label_medium_variant)
                            PreviewStyle.MediumItalic -> stringResource(R.string.label_medium_variant) + " " + androidx.compose.ui.res.stringResource(R.string.label_italic_variant)
                            PreviewStyle.Bold, PreviewStyle.BoldItalic -> stringResource(R.string.preview_bold_variant)
                        }
                        Text(
                            header,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if ((selectedStyle == PreviewStyle.Bold || selectedStyle == PreviewStyle.BoldItalic) && boldFontName != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                boldFontName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        val displayText = customText.ifBlank { defaultSampleText }
                        AndroidView(
                            factory = { ctx ->
                                TextView(ctx).apply {
                                    text = displayText
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                                    setTextColor(onSurfaceColor)
                                    typeface = currentTypeface
                                }
                            },
                            update = { tv ->
                                tv.text = displayText
                                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                                tv.setTextColor(onSurfaceColor)
                                tv.typeface = currentTypeface
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.preview_alphabet),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        val alphabetText = stringResource(R.string.sample_alphabet)
                        AndroidView(
                            factory = { ctx ->
                                TextView(ctx).apply {
                                    text = alphabetText
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                                    setTextColor(onSurfaceColor)
                                    typeface = currentTypeface
                                }
                            },
                            update = { tv ->
                                tv.text = alphabetText
                                tv.setTextColor(onSurfaceColor)
                                tv.typeface = currentTypeface
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.preview_numbers_symbols),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        val numbersText = "0123456789\n!@#\$%^&*()_+-=[]{}|;':\",./<>?"
                        AndroidView(
                            factory = { ctx ->
                                TextView(ctx).apply {
                                    text = numbersText
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                                    setTextColor(onSurfaceColor)
                                    typeface = currentTypeface
                                }
                            },
                            update = { tv ->
                                tv.text = numbersText
                                tv.setTextColor(onSurfaceColor)
                                tv.typeface = currentTypeface
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
    var shizukuAuthorized by remember { mutableStateOf(false) }
    var shizukuRunning by remember { mutableStateOf(false) }

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
    
    fun checkShizukuStatus() {
        val running = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        shizukuRunning = running
        shizukuAuthorized = if (running) ShizukuAPI.hasPermission() else false
    }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkShizukuStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val uninstallLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { 
        scope.launch {
            delay(500)
            CacheCleanupUtils.cleanup(context.cacheDir)
            refreshFonts()
        }
    }
    LaunchedEffect(Unit) { refreshFonts() }
    val fontTypefaces = remember { mutableStateMapOf<String, AndroidTypefaceLegacy?>() }
    val extractedPreviewFiles = remember { mutableStateMapOf<String, File>() }
    LaunchedEffect(installedFonts) {
        val uninstalledPackages = extractedPreviewFiles.keys - installedFonts.toSet()
        uninstalledPackages.forEach { pkg ->
            extractedPreviewFiles[pkg]?.let { file ->
                try { file.delete() } catch (_: Exception) {}
            }
            extractedPreviewFiles.remove(pkg)
            fontTypefaces.remove(pkg)
        }
        installedFonts.forEach { pkg ->
            if (!fontTypefaces.containsKey(pkg)) {
                try {
                    val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
                    val apkFile = File(appInfo.sourceDir)
                    val ttfFile = FontInstallerUtils.extractFontPreview(apkFile, context.cacheDir)
                    if (ttfFile != null) {
                        extractedPreviewFiles[pkg] = ttfFile
                        val tf = AndroidTypefaceLegacy.createFromFile(ttfFile)
                        fontTypefaces[pkg] = tf
                    } else {
                        fontTypefaces[pkg] = null
                    }
                } catch (_: Exception) {
                    fontTypefaces[pkg] = null
                }
            }
        }
    }
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
            Text(androidx.compose.ui.res.stringResource(R.string.title_manage), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 16.dp))
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(androidx.compose.ui.res.stringResource(R.string.section_about), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    val versionName = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        } catch (_: Exception) {
                            "Unknown"
                        }
                    }
                    Text(androidx.compose.ui.res.stringResource(R.string.app_name) + " " + versionName, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(androidx.compose.ui.res.stringResource(R.string.section_installed_fonts), style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.size(48.dp), Alignment.Center) {
                            if (isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else IconButton(onClick = { refreshFonts() }) { Icon(Icons.Default.Refresh, null) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (filteredFonts.isNotEmpty()) {
                        filteredFonts.forEach { pkg ->
                            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(pkg.substringAfterLast(".").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, style = MaterialTheme.typography.bodyLarge)
                                        Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        fontTypefaces[pkg]?.let { tf ->
                                            Spacer(Modifier.height(4.dp))
                                            val previewText = sampleText(false)
                                            AndroidView(
                                                factory = { ctx -> makeSampleTextView(ctx, 14f, onSurfaceColor, previewText) },
                                                update = { tv -> tv.typeface = tf; tv.text = previewText; tv.setTextColor(onSurfaceColor) },
                                                modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                    IconButton({
                                        scope.launch {
                                            if (ShizukuAPI.shouldUseShizuku(context)) {
                                                val success = ShizukuAPI.uninstall(pkg)
                                                Toast.makeText(context, context.getString(if (success) R.string.toast_uninstalled else R.string.toast_uninstall_failed), Toast.LENGTH_SHORT).show()
                                                if (success) { CacheCleanupUtils.cleanup(context.cacheDir); delay(500); refreshFonts() }
                                            } else {
                                                uninstallLauncher.launch(Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply { data = Uri.parse("package:$pkg") })
                                            }
                                        }
                                    }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    } else Text(androidx.compose.ui.res.stringResource(R.string.no_custom_fonts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(androidx.compose.ui.res.stringResource(R.string.section_shizuku), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    val shizukuInstalled = remember { ShizukuAPI.isInstalled() }
                    LaunchedEffect(shizukuInstalled) { checkShizukuStatus() }
                    val shizukuStatus = when {
                        !shizukuInstalled -> context.getString(R.string.shizuku_not_installed)
                        shizukuRunning && shizukuAuthorized -> context.getString(R.string.shizuku_running_authorized)
                        shizukuRunning -> context.getString(R.string.shizuku_running_unauthorized)
                        else -> context.getString(R.string.shizuku_not_running)
                    }
                    Text(androidx.compose.ui.res.stringResource(R.string.shizuku_status, shizukuStatus), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                    if (shizukuInstalled && shizukuRunning && !shizukuAuthorized) {
                        Button(onClick = {
                            ShizukuAPI.requestPermission(
                                onGranted = { shizukuAuthorized = true; Toast.makeText(context, context.getString(R.string.toast_permission_granted), Toast.LENGTH_SHORT).show() },
                                onDenied = { shizukuAuthorized = false; Toast.makeText(context, context.getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show() }
                            )
                        }) { Text(androidx.compose.ui.res.stringResource(R.string.button_request_permission)) }
                    }
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
        onComplete: (Boolean) -> Unit = {},
        boldTtfFile: File? = null
    ) = withContext(Dispatchers.IO) {
        val outputApk = File(context.cacheDir, "signed_${System.currentTimeMillis()}.apk")
        try {
            val fontName = displayName.replace(Regex("[^a-zA-Z0-9]"), "")
            val config = FontBuilder.FontConfig(displayName = displayName, fontName = fontName, ttfFile = ttfFile, boldTtfFile = boldTtfFile)
            if (isAppInstalled(context, config.packageName)) {
                withContext(Dispatchers.Main) { onAlreadyInstalled() }
                return@withContext
            }

            if (!FontBuilder.buildAndSignFontApk(context, config, outputApk)) {
                Log.e(TAG, "buildAndSignFontApk failed")
                withContext(Dispatchers.Main) { onComplete(false) }
                return@withContext
            }
            if (ShizukuAPI.isUsable()) {
                val success = ShizukuAPI.installApk(outputApk) { fallbackApk ->
                    ShizukuAPI.fallbackInstall(context, fallbackApk)
                }
                withContext(Dispatchers.Main) { onComplete(success) }
            } else {
                withContext(Dispatchers.Main) {
                    installApk(context, outputApk, installLauncher)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error installing font", e)
            withContext(Dispatchers.Main) { onComplete(false) }
        } finally {
            if (ShizukuAPI.isUsable()) {
                try { outputApk.delete() } catch (_: Exception) {}
            }
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