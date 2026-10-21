import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.wordtrigger"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.wordtrigger"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86")
            abiFilters.add("x86_64")
        }

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        val llmApiKey = (localProperties.getProperty("LLM_API_KEY") ?: "").trim()
        val llmBaseUrl = (localProperties.getProperty("LLM_BASE_URL") ?: "https://openrouter.ai/api/v1").trim()
        val llmModels = localProperties.getProperty("LLM_MODELS")
            ?: localProperties.getProperty("LLM_MODEL")
            ?: "google/gemma-4-26b-a4b-it:free,nvidia/nemotron-3-ultra-550b-a55b:free"
        val llmProject = localProperties.getProperty("LLM_PROJECT") ?: ""

        buildConfigField("String", "LLM_API_KEY", "\"$llmApiKey\"")
        buildConfigField("String", "LLM_BASE_URL", "\"$llmBaseUrl\"")
        buildConfigField("String", "LLM_MODELS", "\"$llmModels\"")
        buildConfigField("String", "LLM_PROJECT", "\"$llmProject\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}