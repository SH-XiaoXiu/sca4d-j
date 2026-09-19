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
    constraints {
        implementation(libs.httpclient) {
            because("googleauth 1.5.0 声明的 HttpClient 4.5.12 包含已知安全漏洞")
        }
    }

    api(project(":core:sca4dj-core-api"))
    implementation(project(":core:sca4dj-core-spring"))
    api(libs.mybatis)
    implementation(libs.googleauth)
    api(libs.jackson.databind)
    api(libs.mybatis.spring)
    implementation(libs.mybatis.spring.boot.starter)
    api(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter.data.redis)
    api(libs.spring.data.redis)
    api(libs.spring.tx)
    implementation(libs.slf4j.api)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    add("integrationTestImplementation", libs.spring.jdbc)
    add("integrationTestImplementation", libs.testcontainers.postgresql)
    add("integrationTestImplementation", libs.testcontainers.junit.jupiter)
    add("integrationTestRuntimeOnly", libs.postgresql)
    add("integrationTestRuntimeOnly", libs.junit.platform.launcher)
}
