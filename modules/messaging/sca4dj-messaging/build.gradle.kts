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
    implementation(project(":core:sca4dj-core-api"))
    implementation(project(":core:sca4dj-core-spring"))
    api(libs.mybatis)
    api(libs.mybatis.spring)
    implementation(libs.mybatis.spring.boot.starter)
    implementation(libs.rabbitmq.amqp.client)
    implementation(libs.spring.amqp)
    implementation(libs.spring.rabbit)
    api(libs.spring.tx)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    api(libs.spring.boot.autoconfigure)
    compileOnlyApi(libs.jetbrains.annotations)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    add("integrationTestImplementation", libs.spring.boot.testcontainers)
    add("integrationTestImplementation", libs.spring.jdbc)
    add("integrationTestImplementation", libs.spring.amqp)
    add("integrationTestImplementation", libs.testcontainers.postgresql)
    add("integrationTestImplementation", libs.testcontainers.rabbitmq)
    add("integrationTestImplementation", libs.testcontainers.junit.jupiter)
    add("integrationTestRuntimeOnly", libs.postgresql)
    add("integrationTestRuntimeOnly", libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.platform.launcher)
}
