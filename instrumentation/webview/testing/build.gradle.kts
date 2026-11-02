plugins {
    id("otel.android-app-conventions")
    id("net.bytebuddy.byte-buddy-gradle-plugin")
}

android {
    namespace = "io.opentelemetry.android.webview.test"
}

dependencies {
    byteBuddy(project(":instrumentation:webview:agent"))
    implementation(project(":instrumentation:android-instrumentation"))
    implementation(project(":instrumentation:webview:library"))
    implementation(project(":agent-api"))
    implementation(project(":test-common"))

    androidTestImplementation(libs.assertj.core)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.webkit)
}
