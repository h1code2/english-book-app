import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

// ---------------------------------------------------------------------------
// 离线语音引擎（sherpa-onnx + Piper 模型）
// 首次构建自动下载到 app/libs 与 assets；文件已存在则跳过（可离线重复构建）。
// ---------------------------------------------------------------------------
val sherpaVersion = "1.13.7"
val sherpaAar = file("libs/sherpa-onnx-$sherpaVersion.aar")
val sherpaAarUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
val ttsAssetsDir = file("src/main/assets/tts")
val ttsModelUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2"

val fetchSherpaAar by tasks.registering {
    outputs.file(sherpaAar)
    doLast {
        if (!sherpaAar.exists()) {
            sherpaAar.parentFile.mkdirs()
            exec {
                commandLine("curl", "-L", "--fail", "-o", sherpaAar.absolutePath, sherpaAarUrl)
            }
        }
    }
}

val fetchTtsModel by tasks.registering {
    outputs.dir(ttsAssetsDir)
    doLast {
        val onnx = file("$ttsAssetsDir/en_US-lessac-medium.onnx")
        if (!onnx.exists()) {
            ttsAssetsDir.mkdirs()
            val tmp = file("$ttsAssetsDir/../tts-model.tar.bz2")
            exec {
                commandLine("curl", "-L", "--fail", "-o", tmp.absolutePath, ttsModelUrl)
            }
            val extractDir = file("${ttsAssetsDir.parentFile.absolutePath}/tts-model-extract")
            extractDir.mkdirs()
            exec {
                commandLine("tar", "-xjf", tmp.absolutePath, "-C", extractDir.absolutePath)
            }
            copy {
                from(file("$extractDir/vits-piper-en_US-lessac-medium")) {
                    exclude("MODEL_CARD")
                }
                into(ttsAssetsDir)
            }
            delete(tmp, extractDir)
        }
    }
}

// 正式签名配置（keystore.properties 不入库；缺失时 release 回退 debug 签名）
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "org.h1code2.english.notebook"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.h1code2.english.notebook"
        minSdk = 26
        targetSdk = 34
        versionCode = 13
        versionName = "1.8.0"

        // 只保留主流 ABI（x86/x86_64 仅模拟器调试用，会显著增大 APK）
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig =
                if (keystoreProps.isNotEmpty()) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(files("libs/sherpa-onnx-$sherpaVersion.aar"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}

// 离线语音依赖首次构建时自动下载
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(fetchSherpaAar, fetchTtsModel)
}
