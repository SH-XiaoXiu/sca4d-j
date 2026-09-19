plugins {
    `java-library`
    `java-test-fixtures`
    id("com.sca4dj.build.java")
    id("com.sca4dj.build.publish")
}

dependencies {
    api(libs.hutool.all)
    compileOnlyApi(libs.jetbrains.annotations)
    compileOnly(libs.spotbugs.annotations)
    implementation(libs.slf4j.api)
    api(libs.jackson.databind)
    implementation(libs.libphonenumber)

    testImplementation(libs.junit.jupiter.api)
    testFixturesImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.logback.classic)
    testRuntimeOnly(libs.junit.platform.launcher)
}
