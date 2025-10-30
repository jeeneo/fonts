plugins {
    id("com.android.application")
}

android {
    namespace = "PLACEHOLDER_FONT_DISPLAY_NAME_LONG_STRING_HERE_SPACE_SPACE_SPACE"
    compileSdk {
        version = release(36)
    }
    defaultConfig {
        applicationId = "com.monotype.android.font.PLACEHOLDER_FONT_NAME_HI_I_SUCK_AT_MAKING_APPS_SPACE_SPACE"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = null
        }
    }
}

dependencies {
}