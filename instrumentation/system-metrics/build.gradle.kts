plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android system metrics instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.systemmetrics"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(platform(libs.opentelemetry.platform.alpha)) // Required for sonatype publishing
    implementation(project(":instrumentation:android-instrumentation"))
    implementation(project(":common"))
    implementation(libs.opentelemetry.sdk)
    testImplementation(libs.robolectric)
}
