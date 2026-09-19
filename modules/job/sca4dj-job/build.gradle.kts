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
    implementation(libs.mybatis)
    implementation(libs.mybatis.spring)
    implementation(libs.mybatis.spring.boot.autoconfigure)
    implementation(libs.mybatis.spring.boot.starter)
    api(libs.spring.boot.autoconfigure)
    implementation(libs.spring.jdbc)
    implementation(libs.spring.tx)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    compileOnlyApi(libs.jetbrains.annotations)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    add("integrationTestImplementation", libs.testcontainers.postgresql)
    add("integrationTestImplementation", libs.testcontainers.junit.jupiter)
    add("integrationTestRuntimeOnly", libs.postgresql)
    add("integrationTestRuntimeOnly", libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.platform.launcher)
}
