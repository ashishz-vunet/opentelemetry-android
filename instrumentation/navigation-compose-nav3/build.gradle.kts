plugins {
    id("otel.android-library-conventions")
    id("otel.publish-conventions")
    alias(libs.plugins.kotlin.compose)
}

// AGP 9 does not apply KotlinBasePluginWrapper, so the Compose plugin cannot auto-detect
// the Kotlin version for kotlin-compose-compiler-plugin-embeddable. Pin it explicitly.
configurations.matching { it.name.startsWith("kotlinCompilerPluginClasspath") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-compose-compiler-plugin-embeddable") {
            useVersion(libs.versions.kotlin.get())
        }
    }
}

description = "OpenTelemetry Android Compose Navigation 3 instrumentation"

android {
    namespace = "io.opentelemetry.android.instrumentation.navigation.compose.nav3"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
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
