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
    api(project(":core:sca4dj-core-spring"))
    api(project(":modules:iam:sca4dj-iam"))
    api(project(":modules:auth:sca4dj-auth"))
    api(project(":modules:auth:sca4dj-auth-shared-secret"))
    api(project(":modules:auth:sca4dj-auth-hmac"))
    api(libs.spring.webmvc)
    api(libs.mybatis)
    api(libs.mybatis.spring)
    implementation(libs.mybatis.spring.boot.starter)
    api(libs.spring.tx)
    api(libs.jakarta.servlet)
    api(libs.jetbrains.annotations)
    compileOnlyApi(libs.swagger.annotations)
    compileOnly(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.configuration.processor)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.springdoc.openapi.starter.webmvc.ui)
    add("integrationTestImplementation", libs.spring.jdbc)
    add("integrationTestImplementation", libs.testcontainers.postgresql)
    add("integrationTestImplementation", libs.testcontainers.junit.jupiter)
    add("integrationTestRuntimeOnly", libs.postgresql)
    add("integrationTestRuntimeOnly", libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.platform.launcher)
}
