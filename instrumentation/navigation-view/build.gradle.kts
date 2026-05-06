plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android view-based navigation instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.navigation.view"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(platform(libs.opentelemetry.platform.alpha)) // Required for sonatype publishing

    implementation(project(":agent-api"))
    implementation(project(":common"))
    implementation(project(":instrumentation:android-instrumentation"))
    implementation(project(":instrumentation:common-api"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.fragment)
    implementation(libs.opentelemetry.sdk)

    testImplementation(project(":test-common"))
}
