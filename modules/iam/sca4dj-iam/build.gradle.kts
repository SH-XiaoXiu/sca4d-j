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
    api(project(":core:sca4dj-core-spring"))
    compileOnlyApi(libs.jetbrains.annotations)
    api(libs.mybatis)
    api(libs.mybatis.spring)
    api(libs.mybatis.spring.boot.autoconfigure)
    implementation(libs.mybatis.spring.boot.starter)
    api(libs.spring.tx)
    api(libs.jackson.databind)
    runtimeOnly(libs.jackson.datatype.jsr310)
    implementation(libs.slf4j.api)
    compileOnly(libs.spring.data.redis)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.data.redis)
    testRuntimeOnly(libs.junit.platform.launcher)
    add("integrationTestImplementation", libs.testcontainers.postgresql)
    add("integrationTestImplementation", libs.testcontainers.junit.jupiter)
    add("integrationTestRuntimeOnly", libs.postgresql)
    add("integrationTestRuntimeOnly", libs.junit.platform.launcher)
}
