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
    api(project(":modules:open:sca4dj-open"))
    api(libs.spring.webmvc)
    api(libs.jakarta.servlet)
    api(libs.jetbrains.annotations)
    implementation(project(":modules:auth:sca4dj-auth-hmac"))
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.configuration.processor)
    compileOnlyApi(libs.swagger.annotations)
    compileOnly(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
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
