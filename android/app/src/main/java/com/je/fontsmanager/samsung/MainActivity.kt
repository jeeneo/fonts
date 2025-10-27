package com.je.fontsmanager.samsung

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.je.fontsmanager.samsung.ui.MainScreen
import com.je.fontsmanager.samsung.util.ShizukuAPI
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuAPI.init(this)
        ShizukuAPI.requestPermission(
            // onGranted = { Toast.makeText(this, "Shizuku ready", Toast.LENGTH_SHORT).show() },
        )
        enableEdgeToEdge()
        setContent {
            FontInstallerTheme {
                MainScreen()
            }
        }
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
