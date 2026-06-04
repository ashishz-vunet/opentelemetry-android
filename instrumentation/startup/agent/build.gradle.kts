plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry build-time auto-instrumentation for Application startup on Android"

android {
    namespace = "io.opentelemetry.android.instrumentation.startup.agent"
}

dependencies {
    implementation(project(":instrumentation:startup:library"))
    implementation(libs.byteBuddy)
    testImplementation(project(":test-common"))
}
