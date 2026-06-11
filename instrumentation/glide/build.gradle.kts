plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    id("com.google.devtools.ksp")
}

description = "OpenTelemetry Android Glide image-loading instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.glide"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(platform(libs.opentelemetry.platform.alpha)) // Required for sonatype publishing

    implementation(project(":common"))
    implementation(project(":instrumentation:android-instrumentation"))

    implementation(libs.opentelemetry.api)

    // Glide is compileOnly: the module must compile against the Glide API surface but must
    // never force Glide onto consumers that do not already depend on it themselves.
    compileOnly(libs.glide)
    ksp(libs.glide.ksp)

    testImplementation(project(":test-common"))
    testImplementation(libs.glide)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
