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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")

    // 🔥 App Check
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")

    // 🔥 Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    // 🎨 Lottie Animation
    implementation("com.airbnb.android:lottie:6.0.0")

    // 📦 JSON Parsing
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
