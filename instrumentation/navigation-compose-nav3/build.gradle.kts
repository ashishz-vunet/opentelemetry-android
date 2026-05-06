plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
}

description = "OpenTelemetry Android Compose Navigation 3 instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.navigation.compose.nav3"

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
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.opentelemetry.sdk)

    compileOnly(libs.auto.service.annotations)
    ksp(libs.auto.service.processor)

    testImplementation(project(":test-common"))
}
