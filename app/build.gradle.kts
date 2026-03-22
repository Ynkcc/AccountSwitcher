import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { input ->
            load(input)
        }
    }
}

val releaseStoreFile =
    (System.getenv("RELEASE_KEYSTORE_BASE64")
        ?: localProperties.getProperty("release.keystoreBase64")
        ?: (project.findProperty("RELEASE_KEYSTORE_BASE64") as String?))
        ?.takeIf { it.isNotBlank() }
        ?.let { encodedKeystore ->
            val decodedStoreFile = layout.buildDirectory.file("keystore/release.jks").get().asFile
            decodedStoreFile.parentFile?.mkdirs()
            decodedStoreFile.writeBytes(Base64.getDecoder().decode(encodedKeystore.trim()))
            decodedStoreFile
        }
        ?: localProperties.getProperty("release.storeFile")?.takeIf { it.isNotBlank() }?.let(::file)
val releaseStorePassword =
    System.getenv("RELEASE_STORE_PASSWORD")
        ?: localProperties.getProperty("release.storePassword")
        ?: (project.findProperty("RELEASE_STORE_PASSWORD") as String?)
val releaseKeyAlias =
    localProperties.getProperty("release.keyAlias")
        ?: "key0"
val releaseKeyPassword =
    System.getenv("RELEASE_KEY_PASSWORD")
        ?: localProperties.getProperty("release.keyPassword")
        ?: (project.findProperty("RELEASE_KEY_PASSWORD") as String?)

android {
    namespace = "com.tencent.tim"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tencent.tim"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    buildFeatures {
        compose = true
        aidl = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.scalars)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}