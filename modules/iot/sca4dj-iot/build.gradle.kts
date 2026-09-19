plugins {
    `java-library`
    id("com.sca4dj.build.java")
    id("com.sca4dj.build.publish")
    id("io.spring.dependency-management")
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
    api(project(":modules:iot:sca4dj-iot-api"))
    implementation(project(":core:sca4dj-core-spring"))
    implementation(libs.jackson.databind)
    implementation(libs.mybatis)
    implementation(libs.mybatis.spring)
    implementation(libs.mybatis.spring.boot.autoconfigure)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.flyway.core)
    implementation(libs.spring.jdbc)
    runtimeOnly(libs.flyway.postgresql)
    implementation(libs.spring.tx)
    implementation(libs.slf4j.api)
    compileOnlyApi(libs.jetbrains.annotations)
    compileOnly(libs.spotbugs.annotations)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    add("integrationTestImplementation", libs.testcontainers.core)
    add("integrationTestImplementation", libs.testcontainers.postgresql)
    add("integrationTestImplementation", libs.testcontainers.junit.jupiter)
    add("integrationTestRuntimeOnly", libs.lettuce.core)
    add("integrationTestRuntimeOnly", libs.postgresql)
    add("integrationTestRuntimeOnly", libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.platform.launcher)
}
