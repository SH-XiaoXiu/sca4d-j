plugins {
    `java-library`
    id("com.sca4dj.build.java")
    id("com.sca4dj.build.publish")
    id("io.spring.dependency-management")
}

val spotbugsAnnotations by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
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
    api(libs.sa.token.core)
    implementation(libs.sa.token.spring.boot.starter)
    runtimeOnly(libs.sa.token.redis.template)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.tx)
    api(libs.spring.data.redis)
    api(libs.jackson.databind)
    runtimeOnly(libs.jackson.datatype.jsr310)
    implementation(libs.hutool.all)
    implementation(libs.roaringbitmap)
    implementation(libs.slf4j.api)
    implementation(libs.spring.webmvc)
    compileOnly(libs.jakarta.servlet)
    compileOnly(libs.spotbugs.annotations)
    spotbugsAnnotations(libs.spotbugs.annotations)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.jakarta.servlet)
    testImplementation(libs.sa.token.redis.template)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    spotbugsClasspath.from(spotbugsAnnotations)
}
