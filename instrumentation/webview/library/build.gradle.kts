plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android WebView library instrumentation for Android"

android {
    namespace = "io.opentelemetry.android.webview.library"
}

dependencies {
    api(platform(libs.opentelemetry.platform.alpha)) // Required for sonatype publishing
    implementation(project(":common"))
    implementation(project(":services"))
    implementation(project(":session"))
    implementation(project(":instrumentation:android-instrumentation"))
    implementation(project(":agent-api"))
    implementation(libs.androidx.webkit)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
}
