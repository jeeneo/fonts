package com.je.fontsmanager.samsung.ui

enum class PreviewStyle(
    val label: String,
    val weight: Int,
    val italic: Boolean,
    val prefersBoldTf: Boolean
) {
    Regular("Regular", 400, false, false),
    Italic("Italic", 400, true, false),
    Medium("Medium", 500, false, false),
    MediumItalic("Medium Italic", 500, true, false),
    Bold("Bold", 700, false, true),
    BoldItalic("Bold Italic", 700, true, true)
}