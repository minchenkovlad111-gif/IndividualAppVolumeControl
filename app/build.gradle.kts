plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fuck.individualappaudiovolumecontrol.droidvendorssuck"
    compileSdk = 35

    defaultConfig {
        applicationId = "fuck.individualappaudiovolumecontrol.droidvendorssuck"
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
            // Keep the module APK signed for privileged installs without shipping a debuggable build.
            signingConfig = signingConfigs.getByName("debug")
        }
        create("probe") {
            initWith(getByName("release"))
            isDebuggable = false
            matchingFallbacks += listOf("release")
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
    add("legacyImplementation", "androidx.core:core-ktx:1.6.0")
    add("legacyImplementation", "androidx.appcompat:appcompat:1.4.2")
    add("legacyImplementation", "androidx.multidex:multidex:2.0.1")
}

tasks.register("printApkPaths") {
    group = "build"
    description = "Prints expected APK output paths after build."
    dependsOn("packageMagiskModule")
    doLast {
        val apkDir = layout.buildDirectory.dir("outputs/apk").get().asFile
        val paths = listOf(
            "modern/debug/app-modern-debug.apk",
            "legacy/debug/app-legacy-debug.apk",
            "modern/release/app-modern-release.apk",
            "modern/release/app-modern-release-unsigned.apk",
            "legacy/release/app-legacy-release.apk",
            "legacy/release/app-legacy-release-unsigned.apk",
        )
        println("APK outputs:")
        for (relPath in paths) {
            val file = File(apkDir, relPath)
            val status = if (file.exists()) "exists" else "missing"
            println("- ${file.path} ($status)")
        }
        val moduleZip = rootProject.projectDir.resolve("IndividualAppAudioVolumeControl-magisk.zip")
        val moduleStatus = if (moduleZip.exists()) "exists" else "missing"
        println("Magisk module output:")
        println("- ${moduleZip.path} ($moduleStatus)")
    }
}

tasks.register<Exec>("packageMagiskModule") {
    group = "build"
    description = "Packages the Magisk module using the signed modern release APK."
    val apkPath = layout.buildDirectory.file("outputs/apk/modern/release/app-modern-release.apk")
    val outZip = rootProject.layout.projectDirectory.file("IndividualAppAudioVolumeControl-magisk.zip")
    val moduleScript = rootProject.layout.projectDirectory.file("module/package.sh")
    dependsOn("assembleModernRelease")
    executable = "sh"
    commandLine(
        "sh",
        moduleScript.asFile.path,
        apkPath.get().asFile.path,
        outZip.asFile.path,
    )
}

tasks.named("build") {
    finalizedBy("printApkPaths")
}
