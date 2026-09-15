import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// One build variant and one reader. SPEC 8.10's optional cloud parser was removed: it was
// never built, had no key, and was the only thing in the tree that could open a socket, in
// an app whose entire claim is that it cannot.
/**
 * Release signing, read from a file that is never committed.
 *
 * Absent that file the release build falls back to the debug key so it can still be built
 * and run locally, which is the only way to test what R8 actually produces. It cannot be
 * shipped that way by accident: Play rejects a debug-signed upload outright.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val hasReleaseKey = keystoreProperties.getProperty("storeFile") != null

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

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 on, because an unminified release is a larger download for no benefit and
            // because the rules have to be exercised at some point: better now, against the
            // test suite, than the first time a stranger installs it.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Only when running the suite against this build. See the file's own comment.
            if (project.hasProperty("testRelease")) {
                proguardFile("proguard-test-harness.pro")
                // The instrumentation APK is minified separately, with its own rule set.
                testProguardFile("proguard-test-harness.pro")
            }
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                logger.lifecycle(
                    "No keystore.properties: signing release with the debug key. "
                            + "Fine for local testing, rejected by Play."
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    // Instrumented tests run against the minified build, so the ProGuard rules are proved
    // by the same suite that proves the app rather than by hoping.
    testBuildType = if (project.hasProperty("testRelease")) "release" else "debug"

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
