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
val backupUrls = (findProperty("backupUrls") as String?) ?: ""
val appCodename = (findProperty("codename") as String?) ?: "Astra"
val appCodenameKind = (findProperty("codenameKind") as String?) ?: "star"
val appDisplay = (findProperty("glydeDisplay") as String?) ?: "Glyde Dev"
val appRing = (findProperty("ring") as String?) ?: "stable"
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
        buildConfigField("String", "GITHUB_REPO", "\"GrapeGG-developer/glyde\"")
        buildConfigField("String", "BACKUP_URLS", "\"$backupUrls\"")
        buildConfigField("String", "CODENAME", "\"$appCodename\"")
        buildConfigField("String", "CODENAME_KIND", "\"$appCodenameKind\"")
        buildConfigField("String", "DISPLAY_NAME", "\"$appDisplay\"")
        buildConfigField("String", "RING", "\"$appRing\"")
        buildConfigField("String", "MIRRORS", "\"https://grapegg-developer.github.io/glyde/update.json|https://cdn.jsdelivr.net/gh/GrapeGG-developer/glyde@gh-pages/update.json|https://raw.githubusercontent.com/GrapeGG-developer/glyde/gh-pages/update.json\"")
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
            assets.srcDirs(layout.buildDirectory.dir("genAssets"))
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
    implementation(project(":air"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:native"))
    implementation(project(":feature:telegram"))
}

val copyChangelog by tasks.registering(Copy::class) {
    from(rootProject.file("CHANGELOG.md"))
    into(layout.buildDirectory.dir("genAssets"))
}

tasks.named("preBuild") {
    dependsOn(copyChangelog)
}
