// SPEC 6.6 / PROMPT hard rule 5: the calculation layer is plain Java on the JVM.
// This module deliberately uses the `java-library` plugin and NOT the Android plugin,
// so the Android SDK is absent from the compile classpath and an `android.*` import
// cannot compile. The rule is enforced by javac rather than by review.
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17   // SPEC 4.1
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // SPEC 4.11: one JSON library, used for backup payloads and the optional cloud parser.
    implementation(libs.gson)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
