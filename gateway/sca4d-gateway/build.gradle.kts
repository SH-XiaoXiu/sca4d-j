import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("com.sca4dj.build.java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val buildVersion = rootProject.extra["sca4dBuildVersion"] as String

dependencyManagement {
    imports {
        mavenBom(
            libs.spring.boot.dependencies
                .get()
                .toString(),
        )
        mavenBom(
            libs.spring.cloud.dependencies
                .get()
                .toString(),
        )
    }
}

dependencies {
    implementation(project(":core:sca4dj-core-api"))
    implementation(libs.jetbrains.annotations)
    compileOnly(libs.spotbugs.annotations)
    implementation(libs.slf4j.api)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.cloud.starter.gateway.server.webflux)
    implementation(libs.spring.cloud.starter.loadbalancer)
    runtimeOnly(project(":gateway:sca4dj-gateway-loki"))
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("${project.name}-$buildVersion.jar")
}
