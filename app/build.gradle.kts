import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 正式签名信息放在仓库根目录的 keystore.properties（已 gitignore，不进仓库）。
// 没有这个文件时（例如别人克隆仓库自行编译）自动退化成未签名 / debug 签名，不影响构建。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "cn.alittlecookie.lutista"
    compileSdk = 37

    defaultConfig {
        applicationId = "cn.alittlecookie.lutista"
        minSdk = 29
        targetSdk = 37
        versionCode = 12
        versionName = "1.3.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // 单 ABI 构建用 -PtargetAbi=arm64-v8a 指定。
            // 不要用 -Pandroid.injected.build.abi：那是 Studio 的注入式构建参数，
            // 会让 AGP 在 manifest 里写上 android:testOnly="true"，真机普通安装会被拒。
            val targetAbi = (findProperty("targetAbi") as String?)?.takeIf { it.isNotBlank() }
            abiFilters += if (targetAbi != null) {
                listOf(targetAbi)
            } else {
                listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
        }

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
            }
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // 老机型 / 部分厂商安装器只认 JAR 签名，双签更保险
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 单元测试跑在纯 JVM 上，android.util.Log 之类的桩方法默认会抛
    // "Method d in android.util.Log not mocked"，这里让它们返回默认值。
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            excludes += setOf(
                "lib/armeabi-v7a/libopencv_java5.so",
                "lib/x86/libopencv_java5.so",
                "lib/x86_64/libopencv_java5.so",
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
    buildToolsVersion = "37.0.0"
    ndkVersion = "29.0.14206865"
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.navigation3.runtime)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)

    // ExifInterface
    implementation(libs.exifinterface)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Coil
    implementation(libs.coil.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // DocumentFile (SAF)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Timber
    implementation(libs.timber)

    // OpenCV (arm64-v8a only via packaging excludes above) — replaced with Android-native Bitmap APIs
    // implementation(libs.opencv)

    // MIUIX
    implementation(libs.miuix.ui)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.navigation3.ui)

    // Reorderable
    implementation(libs.reorderable)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
