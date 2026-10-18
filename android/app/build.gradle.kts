plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val vcode = (System.getenv("VCODE") ?: "1").toInt()
android {
    namespace = "uk.co.clearpath360.elevate"
    compileSdk = 35
    defaultConfig {
        applicationId = "uk.co.clearpath360.elevate"
        minSdk = 29
        targetSdk = 34
        versionCode = vcode
        versionName = "1.$vcode"
    }
    signingConfigs {
        create("rel") {
            storeFile = file("release.jks")
            storePassword = "elevate360"
            keyAlias = "elevate"
            keyPassword = "elevate360"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("rel")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
