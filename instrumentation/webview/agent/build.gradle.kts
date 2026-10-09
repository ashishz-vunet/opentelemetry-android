plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android WebView instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.webview"
}

dependencies {
    implementation(project(":agent-api"))

    implementation(libs.androidx.core)
    implementation(libs.byteBuddy)
    implementation(project(":instrumentation:webview:library"))
}
