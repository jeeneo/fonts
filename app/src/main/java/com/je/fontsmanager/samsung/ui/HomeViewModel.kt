package com.je.fontsmanager.samsung.ui

import android.graphics.Typeface as AndroidTypefaceLegacy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeViewModel : ViewModel() {
    var selectedFontFile by mutableStateOf<File?>(null)
        private set
    var selectedFontName by mutableStateOf<String?>(null)
        private set
    var selectedBoldFontFile by mutableStateOf<File?>(null)
        private set
    var selectedBoldFontName by mutableStateOf<String?>(null)
        private set
    var displayName by mutableStateOf("")
        private set
    var previewTypeface by mutableStateOf<AndroidTypefaceLegacy?>(null)
        private set
    var boldPreviewTypeface by mutableStateOf<AndroidTypefaceLegacy?>(null)
        private set

    var lastErrorMessage by mutableStateOf<String?>(null)
        private set

    fun updateDisplayName(name: String) {
        displayName = name
    }

    fun setSelectedFontFile(file: File?, name: String?) {
        selectedFontFile = file
        selectedFontName = name
        loadPreviewTypeface(file, false)
    }

    fun setSelectedBoldFontFile(file: File?, name: String?) {
        selectedBoldFontFile = file
        selectedBoldFontName = name
        loadPreviewTypeface(file, true)
    }

    private fun loadPreviewTypeface(file: File?, bold: Boolean) {
        if (file == null) {
            if (bold) boldPreviewTypeface = null else previewTypeface = null
            return
        }
        viewModelScope.launch {
            val tf = try {
                withContext(Dispatchers.IO) { AndroidTypefaceLegacy.createFromFile(file) }
            } catch (e: Exception) {
                lastErrorMessage = "Failed to load font preview: ${e.message}"
                null
            }
            if (bold) boldPreviewTypeface = tf else previewTypeface = tf
        }
    }

    fun clearError() { lastErrorMessage = null }

    fun clearSelectedBold() {
        try { selectedBoldFontFile?.delete() } catch (_: Exception) {}
        selectedBoldFontFile = null
        selectedBoldFontName = null
        boldPreviewTypeface = null
    }

    fun clearSelectedRegular() {
        try { selectedFontFile?.delete() } catch (_: Exception) {}
        selectedFontFile = null
        selectedFontName = null
        previewTypeface = null
        displayName = ""
    }
}
