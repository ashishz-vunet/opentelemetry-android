plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android Coil image-loading instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.coil"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(platform(libs.opentelemetry.platform.alpha)) // Required for sonatype publishing

    implementation(project(":common"))
    implementation(project(":instrumentation:android-instrumentation"))

    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.extension.kotlin)

    // Coil is compileOnly: the module must compile against the Coil API surface but must
    // never force Coil onto consumers that do not already depend on it themselves.
    compileOnly("io.coil-kt:coil:2.6.0")

    testImplementation(project(":test-common"))
    testImplementation("io.coil-kt:coil:2.6.0")
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
