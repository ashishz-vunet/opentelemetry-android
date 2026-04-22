plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android hybrid click instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.hybrid.click"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(platform(libs.opentelemetry.platform.alpha)) // Required for sonatype publishing

    implementation(project(":agent-api"))
    implementation(project(":instrumentation:android-instrumentation"))
    implementation(project(":services"))

    compileOnly(libs.compose) {
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "androidx.savedstate")
    }

    implementation(libs.opentelemetry.instrumentation.apiSemconv)
    implementation(libs.opentelemetry.semconv.incubating)

    testImplementation(project(":test-common"))
}
