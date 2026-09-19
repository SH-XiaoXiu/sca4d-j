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
    api(project(":core:sca4dj-core-api"))
    implementation(project(":core:sca4dj-core-spring"))
    api(libs.spring.webmvc)
    implementation(libs.spring.websocket)
    implementation(libs.spring.messaging)
    implementation(libs.spring.amqp)
    implementation(libs.spring.rabbit)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.tx)
    implementation(libs.spring.data.redis)
    implementation(libs.jackson.databind)
    runtimeOnly(libs.jackson.datatype.jsr310)
    implementation(libs.slf4j.api)
    api(libs.spring.boot.autoconfigure)
    compileOnlyApi(libs.jetbrains.annotations)
    compileOnly(libs.spotbugs.annotations)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.jackson.datatype.jsr310)
    add("integrationTestImplementation", libs.spring.boot.testcontainers)
    add("integrationTestImplementation", libs.bucket4j.redisson)
    add("integrationTestImplementation", libs.spring.amqp)
    add("integrationTestImplementation", libs.jakarta.servlet)
    add("integrationTestRuntimeOnly", libs.lettuce.core)
    add("integrationTestImplementation", libs.testcontainers.core)
    add("integrationTestImplementation", libs.testcontainers.rabbitmq)
    add("integrationTestImplementation", libs.testcontainers.junit.jupiter)
    add("integrationTestRuntimeOnly", libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.platform.launcher)
}
