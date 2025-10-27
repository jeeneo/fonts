package com.je.fontsmanager.samsung

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.je.fontsmanager.samsung.ui.MainScreen
import com.je.fontsmanager.samsung.ui.theme.FontInstallerTheme
import com.je.fontsmanager.samsung.util.ShizukuAPI
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    
    private val shizukuRequestCode = 0
    
    private val shizukuPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == shizukuRequestCode) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            if (!granted) ShizukuAPI.permissionDenied = true // <- important
            Toast.makeText(
                this,
                if (granted) "Shizuku permission granted" else "Shizuku permission denied",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuAPI.init(this)
        try {
            if (ShizukuAPI.shouldRequestOnStartup() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(shizukuRequestCode)
            }
        } catch (e: Exception) {
        }
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
        enableEdgeToEdge()
        setContent { FontInstallerTheme { MainScreen() } }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
    }
}