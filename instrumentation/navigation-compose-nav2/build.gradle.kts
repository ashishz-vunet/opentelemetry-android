plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android Compose Navigation 2 instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.navigation.compose.nav2"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(platform(libs.opentelemetry.platform.alpha)) // Required for sonatype publishing

    implementation(project(":agent-api"))
    implementation(project(":instrumentation:android-instrumentation"))
    implementation(project(":instrumentation:common-api"))
    implementation(project(":instrumentation:navigation-common"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.opentelemetry.sdk)

    testImplementation(project(":test-common"))
    testImplementation(libs.androidx.navigation.testing)
}
