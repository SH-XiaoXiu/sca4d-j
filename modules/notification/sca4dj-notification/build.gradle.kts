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
    implementation(project(":core:sca4dj-core-api"))
    implementation(project(":core:sca4dj-core-spring"))
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    implementation(libs.spring.tx)
    api(libs.spring.boot.autoconfigure)
    compileOnlyApi(libs.jetbrains.annotations)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
