plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "rs.plan1k.regulacija"
    compileSdk = 36
    defaultConfig { applicationId = "rs.plan1k.regulacija"; minSdk = 26; targetSdk = 35; versionCode = 15; versionName = "0.15.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
