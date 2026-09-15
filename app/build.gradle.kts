plugins {
    alias(libs.plugins.android.application)
}

// One build variant and one reader. SPEC 8.10's optional cloud parser was removed: it was
// never built, had no key, and was the only thing in the tree that could open a socket, in
// an app whose entire claim is that it cannot.
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

    sourceSets {
        // MigrationTestHelper reads the exported schemas from the test APK's assets, which
        // is what lets it check a hand-written migration against the real thing.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.splashscreen)
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
