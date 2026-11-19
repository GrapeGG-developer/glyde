import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val abis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
val appVersionName = (findProperty("versionName") as String?) ?: "1.0.0"
val appVersionCode = ((findProperty("versionCode") as String?) ?: "1").toInt()
val serverUrl = (findProperty("serverUrl") as String?) ?: "https://gt-unblock-api.onrender.com"
val keystoreFile = System.getenv("GT_KEYSTORE_FILE")

android {
    namespace = "gpr.greentune.unblock"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "gpr.greentune.unblock"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        buildConfigField("String", "GITHUB_REPO", "\"GrapeGG-developer/gt-unblock\"")
        buildConfigField("String", "DONATE_URL", "\"https://pay.cloudtips.ru/p/c9e9cb6d\"")
        ndk {
            abiFilters.addAll(abis)
        }
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = File(keystoreFile)
                storePassword = System.getenv("GT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GT_KEY_ALIAS")
                keyPassword = System.getenv("GT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs(layout.buildDirectory.dir("ndkLibs"))
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*abis.toTypedArray())
            isUniversalApk = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("com.google.zxing:core:3.5.3")
}

val ndkOut = layout.buildDirectory.dir("ndkLibs")

val runNdkBuild by tasks.registering(Exec::class) {
    group = "build"
    val ndkDir = android.ndkDirectory
    val outDir = ndkOut.get().asFile
    val workDir = layout.buildDirectory.dir("ndkBuild").get().asFile
    inputs.dir("src/main/jni")
    outputs.dir(outDir)
    executable = File(ndkDir, "ndk-build").absolutePath
    args(
        "NDK_PROJECT_PATH=${workDir.absolutePath}",
        "NDK_LIBS_OUT=${outDir.absolutePath}",
        "APP_BUILD_SCRIPT=${file("src/main/jni/Android.mk").absolutePath}",
        "NDK_APPLICATION_MK=${file("src/main/jni/Application.mk").absolutePath}",
        "-j${Runtime.getRuntime().availableProcessors()}"
    )
    doLast {
        outDir.listFiles()?.filter { it.isDirectory }?.forEach { abiDir ->
            val exe = File(abiDir, "ciadpi")
            if (exe.exists()) {
                val target = File(abiDir, "libciadpi.so")
                target.delete()
                exe.copyTo(target, overwrite = true)
                exe.delete()
            }
            File(abiDir, "hev-socks5-tunnel-bin").delete()
            File(abiDir, "gdbserver").delete()
            File(abiDir, "gdb.setup").delete()
        }
    }
}

tasks.named("preBuild") {
    dependsOn(runNdkBuild)
}
