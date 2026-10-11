plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Alamat dashboard: dari variabel GitHub (SERVER_URL) saat build di Actions, atau dari environment lokal.
val serverUrl: String = (System.getenv("SERVER_URL") ?: "").trim()
    .ifBlank { "http://192.168.1.10:8080/" }
    .let { if (it.endsWith("/")) it else "$it/" }

// Penandatanganan rilis (opsional): hanya aktif bila KEYSTORE_FILE diisi (lihat README).
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.pandu.monitoringclient"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pandu.monitoringclient"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("rel") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("rel")
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
