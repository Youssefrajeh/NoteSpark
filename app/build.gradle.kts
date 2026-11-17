plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services) // reads app/google-services.json
}

android {
    namespace = "com.group15.notemate"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.group15.notemate"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
    }
    testOptions {
        // android.util.Log does nothing in unit tests instead of throwing
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // AndroidX + Material
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    // MVVM: ViewModel + LiveData
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Coroutines (+ await() for ML Kit Tasks)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.concurrent.futures.ktx) // await() for Gemini Nano's ListenableFutures

    // Firebase: the notes are saved in Firestore, under an anonymous account per phone
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)

    // JSON
    implementation(libs.gson)

    // ML Kit
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.digital.ink) // handwriting, downloads its model once
    implementation(libs.mlkit.entity.extraction) // downloads its model once
    implementation(libs.mlkit.language.id)
    implementation(libs.mlkit.translate) // downloads a model per language
    implementation(libs.mlkit.document.scanner) // Google Play services module, downloaded on first use
    implementation(libs.mlkit.genai.summarization) // Gemini Nano, only on supported phones

    testImplementation(libs.junit)
}
