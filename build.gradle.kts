plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.application)
}

group = "dev.alexadler9.agent"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("MainKt")
    applicationName = "agent-playground"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaExec> {
    systemProperty("file.encoding", "UTF-8")
    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "-Dsun.stdout.encoding=UTF-8",
        "-Dsun.stderr.encoding=UTF-8",
    )
}