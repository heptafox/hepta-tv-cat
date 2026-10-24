plugins { id("com.android.application") }

android {
    namespace = "dev.hepta.tvcat"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.hepta.tvcat"
        minSdk = 26
        targetSdk = 30 // ponytail: matches the TV (Android 11); bump only when the TV updates
        versionCode = 6
        versionName = "0.6"
    }
    // ponytail: checked-in debug key so CI and local builds share one signature and update in place on the TV. Public key = anyone can build an updating APK; fine for a hobby overlay.
    signingConfigs { getByName("debug") { storeFile = file("debug.keystore"); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
