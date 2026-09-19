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
    implementation(libs.webauthn.server.core)
    api(libs.mybatis)
    api(libs.mybatis.spring)
    implementation(libs.mybatis.spring.boot.starter)
    api(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter.data.redis)
    api(libs.spring.data.redis)
    api(libs.spring.tx)
    api(libs.jackson.databind)
    implementation(libs.slf4j.api)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
