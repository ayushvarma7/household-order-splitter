import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// SPEC 8.10.3: the optional cloud parser's key is read from local.properties into
// BuildConfig. It is never hardcoded and never committed; absent a key the Settings
// option disables itself with an explanation.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val llmApiKey: String = localProperties.getProperty("llm.api.key", "")

android {
    namespace = "com.householdsplitter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.householdsplitter"
        minSdk = 26                       // SPEC 4.9
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                // Checked into version control so schema changes are reviewable.
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    // SPEC 4.10: no INTERNET permission in the default build. The cloud parser lives in
    // its own flavour, along with the only source file that can reach the network.
    flavorDimensions += "parser"
    productFlavors {
        create("standard") {
            dimension = "parser"
            isDefault = true
            buildConfigField("String", "LLM_API_KEY", "\"\"")
            buildConfigField("boolean", "CLOUD_PARSER_BUILD", "false")
        }
        create("cloud") {
            dimension = "parser"
            buildConfigField("String", "LLM_API_KEY", "\"$llmApiKey\"")
            buildConfigField("boolean", "CLOUD_PARSER_BUILD", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true      // SPEC 4.2: ViewBinding, no Data Binding expressions
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17   // SPEC 4.1
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.preference)
    implementation(libs.material)

    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.gson)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.contrib)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.room.testing)
}
