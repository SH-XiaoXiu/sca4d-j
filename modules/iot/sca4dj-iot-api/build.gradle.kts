plugins {
    `java-library`
    id("com.sca4dj.build.java")
    id("com.sca4dj.build.publish")
}

dependencies {
    api(project(":core:sca4dj-core-api"))
    api(libs.jackson.annotations)
    compileOnlyApi(libs.jetbrains.annotations)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.jackson.datatype.jsr310)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
