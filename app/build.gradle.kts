plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.duda.appvolumecontrol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.duda.appvolumecontrol"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        multiDexEnabled = true
    }

    flavorDimensions += "sdk"
    productFlavors {
        create("modern") {
            dimension = "sdk"
            minSdk = 26
        }
        create("legacy") {
            dimension = "sdk"
            minSdk = 16
            versionNameSuffix = "-legacy"
        }
    }

    sourceSets {
        getByName("legacy") {
            manifest.srcFile("src/legacy/AndroidManifest.xml")
        }
        getByName("modern") {
            java.srcDir("src/modern/java")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
    add("modernImplementation", "androidx.core:core-ktx:1.13.1")
    add("modernImplementation", "androidx.appcompat:appcompat:1.7.0")
    add("modernImplementation", "com.google.android.gms:play-services-location:21.1.0")
    add("legacyImplementation", "androidx.core:core-ktx:1.6.0")
    add("legacyImplementation", "androidx.appcompat:appcompat:1.4.2")
    add("legacyImplementation", "androidx.multidex:multidex:2.0.1")
}
