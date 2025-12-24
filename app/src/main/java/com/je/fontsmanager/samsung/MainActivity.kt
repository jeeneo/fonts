package com.je.fontsmanager.samsung

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.setContent
import com.je.fontsmanager.samsung.ui.MainScreen
import com.je.fontsmanager.samsung.util.ShizukuAPI
import com.je.fontsmanager.samsung.util.CacheCleanupUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.SideEffect
import androidx.activity.SystemBarStyle

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cleanupCache()
        ShizukuAPI.init(this)
        if (ShizukuAPI.shouldUseShizuku(this)) {
            ShizukuAPI.requestPermission(
                // onGranted = { Toast.makeText(this, "Shizuku ready", Toast.LENGTH_SHORT).show() },
            )
        }
        enableEdgeToEdge()
        setContent {
            val isDarkTheme = isSystemInDarkTheme()
            SideEffect {
                if (!isDarkTheme) {
                    val lightTransparentStyle = SystemBarStyle.light(
                        scrim = android.graphics.Color.TRANSPARENT,
                        darkScrim = android.graphics.Color.TRANSPARENT
                    )
                    enableEdgeToEdge(
                        statusBarStyle = lightTransparentStyle,
                        navigationBarStyle = lightTransparentStyle
                    )
                } else {
                    val darkTransparentStyle = SystemBarStyle.dark(
                        scrim = android.graphics.Color.TRANSPARENT
                    )
                    enableEdgeToEdge(
                        statusBarStyle = darkTransparentStyle,
                        navigationBarStyle = darkTransparentStyle
                    )
                }
            }
            FontInstallerTheme {
                MainScreen()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cleanupCache()
    }
    
    private fun cleanupCache() {
        CacheCleanupUtils.cleanup(cacheDir)
    }
}

@Composable
fun FontInstallerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isNightMode = LocalConfiguration.current.isNightModeActive
    MaterialTheme(
        colorScheme = if (isNightMode) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        },
        content = content
    )
}
