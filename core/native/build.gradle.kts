import java.io.File

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "gpr.greentune.unblock.engine.jni"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

val abis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
val ndkOut = layout.buildDirectory.dir("ndkLibs")

android.sourceSets.getByName("main").jniLibs.srcDirs(ndkOut)

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
