plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotest)

    `java-library`
}

group = "io.grafford.kotest.pact"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.kotlin.coroutines)
    implementation(libs.kotest.framework)
    implementation(libs.pact.consumer)

    testImplementation(libs.kotest.assertions)
}
