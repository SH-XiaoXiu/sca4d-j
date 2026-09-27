plugins {
    `java-library`
    id("com.sca4dj.build.java")
    id("com.sca4dj.build.publish")
}
dependencies {
    api(project(":core:sca4dj-core-api"))
    implementation(libs.pf4j)
    testImplementation(project(":modules:plugin:sca4dj-plugin"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
