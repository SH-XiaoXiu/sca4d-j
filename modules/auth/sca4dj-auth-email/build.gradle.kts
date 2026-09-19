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
    runtimeOnly(project(":core:sca4dj-core-spring"))
    implementation(project(":modules:auth:sca4dj-auth"))
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    api(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.tx)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
