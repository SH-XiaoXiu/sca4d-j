import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("com.sca4dj.build.java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.sca4dj.app")
}

val buildVersion = rootProject.extra["sca4dBuildVersion"] as String

dependencies {
    implementation(project(":core:sca4dj-core-spring"))
    implementation(project(":modules:iam:sca4dj-iam"))
    implementation(project(":modules:auth:sca4dj-auth"))
    implementation(project(":modules:auth:sca4dj-auth-password"))
    implementation(project(":modules:auth:sca4dj-auth-totp"))
    implementation(project(":modules:auth:sca4dj-auth-passkey"))
    implementation(project(":modules:auth:sca4dj-auth-email"))
    implementation(project(":modules:auth:sca4dj-auth-sms"))
    implementation(project(":modules:auth:sca4dj-auth-federation"))
    implementation(project(":modules:auth:sca4dj-auth-shared-secret"))
    implementation(project(":modules:auth:sca4dj-auth-hmac"))
    implementation(project(":modules:auth:sca4dj-auth-x509"))
    implementation(project(":modules:openapi:sca4dj-openapi"))
    implementation(project(":modules:notification:sca4dj-notification"))
    implementation(project(":modules:notification:sca4dj-notification-email"))
    implementation(project(":modules:notification:sca4dj-notification-inbox"))
    implementation(project(":modules:notification:sca4dj-notification-sms"))
    implementation(project(":modules:notification:sca4dj-notification-sms-aliyun"))
    implementation(project(":modules:iot:sca4dj-iot"))
    implementation(project(":modules:iot:sca4dj-iot-emqx"))
    implementation(project(":modules:iot:sca4dj-iot-timescale"))
    implementation(libs.jackson.annotations)
    implementation(libs.jakarta.servlet)
    implementation(libs.jakarta.validation)
    implementation(libs.jetbrains.annotations)
    compileOnly(libs.spotbugs.annotations)
    implementation(libs.mybatis)
    implementation(libs.mybatis.spring)
    implementation(libs.slf4j.api)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.springdoc.openapi.starter.common)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.spring.jdbc)
    implementation(libs.swagger.annotations)
    implementation(libs.swagger.models)
    implementation(project(":modules:audit:sca4dj-audit"))
    runtimeOnly(project(":modules:messaging:sca4dj-messaging"))
    runtimeOnly(project(":modules:job:sca4dj-job"))
    runtimeOnly(project(":modules:scheduling:sca4dj-scheduling-quartz"))
    implementation(project(":modules:web-realtime:sca4dj-web-realtime"))
    runtimeOnly(project(":modules:storage:sca4dj-storage-file"))
    runtimeOnly(project(":modules:storage:sca4dj-storage-local"))
    runtimeOnly(project(":modules:storage:sca4dj-storage-minio"))
    runtimeOnly(libs.zxing.javase)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    add("integrationTestImplementation", project(":modules:auth:sca4dj-auth"))
    add("integrationTestImplementation", project(":modules:storage:sca4dj-storage-file"))
    add("integrationTestImplementation", libs.spring.boot.testcontainers)
    add("integrationTestImplementation", libs.spring.jdbc)
    add("integrationTestImplementation", libs.googleauth)
    add("integrationTestImplementation", libs.httpclient)
    add("integrationTestImplementation", libs.jackson.databind)
    add("integrationTestImplementation", libs.zxing.core)
    add("integrationTestImplementation", libs.zxing.javase)
    add("integrationTestImplementation", libs.testcontainers.postgresql)
    add("integrationTestImplementation", libs.testcontainers.rabbitmq)
    add("integrationTestImplementation", libs.testcontainers.core)
    add("integrationTestImplementation", libs.testcontainers.junit.jupiter)
    add("integrationTestRuntimeOnly", libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.platform.launcher)
}

sca4djApp {
    migrationName.set("sca4d")
    schemaFragment.set(layout.projectDirectory.file("src/main/resources/schema/app.sql"))
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("${project.name}-$buildVersion.jar")
}
