plugins { id("com.android.application") }

android {
    namespace = "dev.hepta.tvcat"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.hepta.tvcat"
        minSdk = 26
        targetSdk = 30 // ponytail: matches the TV (Android 11); bump only when the TV updates
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
