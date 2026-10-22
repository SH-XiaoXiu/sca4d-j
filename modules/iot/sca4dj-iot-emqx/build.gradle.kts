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
    implementation(libs.hivemq.mqtt.client)
    implementation(libs.jackson.databind)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.webmvc)
    implementation(libs.slf4j.api)
    compileOnlyApi(libs.jetbrains.annotations)
    compileOnly(libs.spotbugs.annotations)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.retry)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
