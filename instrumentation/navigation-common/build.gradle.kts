plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android shared navigation instrumentation internals"

android {
    namespace = "io.opentelemetry.android.instrumentation.navigation.common"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(platform(libs.opentelemetry.platform.alpha)) // Required for sonatype publishing

    implementation(project(":common"))
    implementation(project(":instrumentation:common-api"))
    implementation(libs.androidx.core)
    implementation(libs.opentelemetry.sdk)
}
