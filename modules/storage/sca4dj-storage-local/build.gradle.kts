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
    api(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(testFixtures(project(":core:sca4dj-core-api")))
    testRuntimeOnly(libs.junit.platform.launcher)
}
