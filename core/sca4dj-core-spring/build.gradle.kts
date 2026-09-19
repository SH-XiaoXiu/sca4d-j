plugins {
    `java-library`
    id("com.sca4dj.build.java")
    id("com.sca4dj.build.publish")
    id("io.spring.dependency-management")
}

val spotbugsAnnotations by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencyManagement {
    imports {
        mavenBom(
            libs.spring.boot.dependencies
                .get()
                .toString(),
        )
    }
}

dependencies {
    api(project(":core:sca4dj-core-api"))
    api(libs.spring.webmvc)
    implementation(libs.spring.tx)
    api(libs.mybatis)
    api(libs.jakarta.validation)
    api(libs.jackson.databind)
    api(libs.bucket4j.core)
    api(libs.redisson)

    compileOnlyApi(libs.jetbrains.annotations)
    compileOnlyApi(libs.jakarta.servlet)
    compileOnlyApi(libs.swagger.annotations)
    compileOnlyApi(libs.swagger.models)
    compileOnly(libs.springdoc.openapi.starter.webmvc.ui)
    compileOnly(libs.spotbugs.annotations)
    spotbugsAnnotations(libs.spotbugs.annotations)
    implementation(libs.slf4j.api)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.bucket4j.redisson)
    implementation(libs.spring.security.crypto)
    api(libs.spring.aspectjweaver)
    api(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.data.redis)
    compileOnly(libs.mybatis.spring)
    compileOnly(libs.mybatis.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)
    runtimeOnly(libs.bouncycastle.provider)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.validation)
    testImplementation(libs.spring.data.redis)
    testImplementation(libs.spring.jdbc)
    testImplementation(libs.mybatis.spring)
    testImplementation(libs.mybatis.spring.boot.autoconfigure)
    testImplementation(libs.springdoc.openapi.starter.webmvc.ui)
    testImplementation(libs.swagger.core)
    testImplementation(libs.jakarta.servlet)
    testRuntimeOnly(libs.swagger.annotations)
    testRuntimeOnly(libs.swagger.models)
    testRuntimeOnly(libs.junit.platform.launcher)
    add("integrationTestImplementation", libs.testcontainers.postgresql)
    add("integrationTestImplementation", libs.testcontainers.junit.jupiter)
    add("integrationTestRuntimeOnly", libs.postgresql)
    add("integrationTestRuntimeOnly", libs.junit.platform.launcher)
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    spotbugsClasspath.from(spotbugsAnnotations)
}
