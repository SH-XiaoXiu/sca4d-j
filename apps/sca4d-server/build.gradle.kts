import org.gradle.api.tasks.testing.Test
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("com.sca4dj.build.java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.sca4dj.app")
    id("com.sca4dj.plugin-api")
}

val buildVersion = rootProject.extra["sca4dBuildVersion"] as String

dependencies {
    implementation(project(":modules:plugin:sca4dj-plugin-spring"))
    add("pluginShared", project(":core:sca4dj-core-api"))
    add("pluginShared", libs.slf4j.api)
    add(
        "pluginProcessor",
        files(
            rootProject.layout.projectDirectory.file(
                "tools/sca4dj-app-gradle-plugin/build/libs/sca4dj-plugin-processor.jar",
            ),
        ).builtBy(gradle.includedBuild("sca4dj-app-gradle-plugin").task(":pluginProcessorJar")),
    )
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
    runtimeOnly(libs.spring.retry)
    runtimeOnly(libs.zxing.javase)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":modules:plugin:sca4dj-plugin"))
    testImplementation(project(":modules:plugin:sca4dj-plugin-pf4j"))
    testImplementation(
        files(
            rootProject.layout.projectDirectory.file(
                "tools/sca4dj-app-gradle-plugin/build/libs/sca4dj-plugin-processor.jar",
            ),
        ).builtBy(gradle.includedBuild("sca4dj-app-gradle-plugin").task(":pluginProcessorJar")),
    )
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

appPluginApi {
    id.set("SCA4D:SERVER")
    version.set("1.1.0")
    generatedPackage.set("com.sca4d.app.server.plugin.api")
    entryName.set("Sca4dServerPlugin")
    configurationType.set("com.sca4d.app.server.plugin.Sca4dServerPluginSettings")
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("${project.name}-$buildVersion.jar")
}

tasks.withType<Test>().configureEach {
    dependsOn(":apps:sca4d-server-mfa-plugin:pluginJar")
    systemProperty(
        "sca4dj.plugin.artifacts[0]",
        rootProject.layout.projectDirectory
            .file(
                "apps/sca4d-server-mfa-plugin/build/libs/sca4d-server-mfa-plugin-1.0.0-plugin.jar",
            ).asFile.absolutePath,
    )
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    dependsOn(":apps:sca4d-server-mfa-plugin:pluginJar")
    environment(
        "SCA4DJ_MFA_PLUGIN",
        rootProject.layout.projectDirectory
            .file(
                "apps/sca4d-server-mfa-plugin/build/libs/sca4d-server-mfa-plugin-1.0.0-plugin.jar",
            ).asFile.absolutePath,
    )
}

tasks.register<Zip>("pluginDistribution") {
    dependsOn("bootJar", ":apps:sca4d-server-mfa-plugin:pluginJar")
    archiveClassifier.set("distribution")
    from(tasks.named<BootJar>("bootJar"))
    from(
        rootProject.layout.projectDirectory.file(
            "apps/sca4d-server-mfa-plugin/build/libs/sca4d-server-mfa-plugin-1.0.0-plugin.jar",
        ),
    ) {
        into("plugins")
        rename { "sca4d-server-mfa.jar" }
    }
}
