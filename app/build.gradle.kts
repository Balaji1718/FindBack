import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.balaji.findback"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.balaji.findback"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")

        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        // API Keys from local.properties
        buildConfigField(
            "String",
            "GROQ_API_KEY",
            "\"${localProperties.getProperty("GROQ_API_KEY") ?: ""}\""
        )

        buildConfigField(
            "String",
            "COHERE_API_KEY",
            "\"${localProperties.getProperty("COHERE_API_KEY") ?: ""}\""
        )

        buildConfigField(
            "String",
            "OPENROUTER_API_KEY",
            "\"${localProperties.getProperty("OPENROUTER_API_KEY") ?: ""}\""
        )

        buildConfigField(
            "String",
            "NVIDIA_API_KEY",
            "\"${localProperties.getProperty("NVIDIA_API_KEY") ?: ""}\""
        )
    }

    // 🔐 Release Signing Configuration
    signingConfigs {
        create("release") {
            storeFile = file("findback-key.jks")

            // Replace with your actual values
            storePassword = "1810@Google"
            keyAlias = "findback"
            keyPassword = "1810@Google"
        }
    }

    buildTypes {

        release {
            isMinifyEnabled = false

            // Connect Release Keystore
            signingConfig = signingConfigs.getByName("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isDebuggable = true
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // 🔥 Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:33.10.0"))

    // Firebase Services
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")

    // 🔐 Firebase App Check
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")

    // 🖼️ Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    // 🎨 Lottie Animations
    implementation("com.airbnb.android:lottie:6.0.0")

    // 📦 JSON Parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // 📝 Typography & Markdown Support
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:image-glide:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
        implementation("io.noties.markwon:simple-ext:4.6.2")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
