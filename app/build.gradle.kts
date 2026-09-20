plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.hamoon.uncleted"
    compileSdk = 34
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.hamoon.uncleted"
        minSdk = 28
        targetSdk = 34
        versionCode = 10
        versionName = "10.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments(
                    "-DANDROID_ARM_MODE=arm",
                    "-DCMAKE_C_FLAGS=-march=armv8.5-a+memtag -fsanitize=memtag",
                    "-DCMAKE_CXX_FLAGS=-march=armv8.5-a+memtag -fsanitize=memtag"
                )
                abiFilters("arm64-v8a")
            }
        }

        ndk {
            abiFilters.clear()
            abiFilters.add("arm64-v8a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            // Release Keystore configuration (falls back to debug if release keystore is absent)
            val releaseKeystore = file("${rootProject.projectDir}/release.keystore")
            if (releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = System.getenv("UNCLETED_KEYSTORE_PASSWORD") ?: "uncleted_release"
                keyAlias = System.getenv("UNCLETED_KEY_ALIAS") ?: "uncleted"
                keyPassword = System.getenv("UNCLETED_KEY_PASSWORD") ?: "uncleted_release"
            } else {
                val debugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storeFile = if (debugKeystore.exists()) debugKeystore else file("${rootProject.projectDir}/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        getByName("debug") {
            val debugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storeFile = if (debugKeystore.exists()) debugKeystore else file("${rootProject.projectDir}/debug.keystore")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/versions/**"
            excludes += "META-INF/OSGI-INF/**"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // --- XPOSED / LSPOSED HOOK API ---
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("de.robv.android.xposed:api:82:sources")

    // --- CRYPTOGRAPHY & BOUNCY CASTLE (ED25519 & ML-KEM-768 PQC ENGINE) ---
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // --- ANDROIDX & MATERIAL ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)

    // --- EMAIL ALERT ENGINE ---
    implementation(libs.sun.mail.android)
    implementation(libs.sun.activation.android)

    // --- PREFERENCES & HARDENED PERSISTENCE ---
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.security.crypto)

    // --- NETWORKING ---
    implementation(libs.squareup.retrofit)
    implementation(libs.squareup.converter.gson)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // --- COROUTINES ---
    implementation(libs.kotlinx.coroutines.android)

    // --- LOCATION SERVICES ---
    implementation(libs.google.play.services.location)

    // --- CAMERAX ENGINE ---
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    // --- LIFECYCLE & WORKMANAGER ---
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.work.runtime.ktx)

    // --- BIOMETRICS & MEDIA ---
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.media:media:1.7.0")

    // --- TESTING ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
