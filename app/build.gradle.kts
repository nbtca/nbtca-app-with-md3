import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("key.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val releaseProperties = Properties().apply {
    rootProject.file("release.properties").inputStream().use { load(it) }
}

android {
    namespace = "space.nbtca.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "space.nbtca.app.md3"
        minSdk = 26
        targetSdk = 35
        // CI 按 tag 传入版本号（如 -PversionName=1.0.1-beta.1）；本地默认与当前 Beta 发布一致
        versionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: releaseProperties.getProperty("versionCode").toInt()
        versionName = (findProperty("versionName") as String?) ?: releaseProperties.getProperty("versionName")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.getProperty("storeFile") != null) {
                storeFile = rootProject.file(
                    keystoreProperties.getProperty("storeFile", ".signing/nbtca-release.jks")
                )
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    val releaseKeystore = keystoreProperties.getProperty("storeFile")
        ?.let { rootProject.file(it) }
        ?.takeIf { it.exists() }

    buildTypes {
        release {
            // 只有本机存在 keystore 时才启用签名；CI 上没配置 secrets 也能出包（未签名）
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.findByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
        create("optimized") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            // 本地精简包沿用调试签名，可以覆盖安装开发版。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        // Release 构建不跑 lintVital，避免 CI 额外拉取 lint 依赖导致构建不稳定
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.browser:browser:1.10.0")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha08")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    // Network & Serialization
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // HTML → Markdown for article bodies (was server-side in the old BFF)
    implementation("org.jsoup:jsoup:1.18.1")

    // Preferences / DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
