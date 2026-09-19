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
    implementation(libs.caffeine)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.jsoup)
    implementation(libs.mustache.compiler)
    implementation(libs.owasp.html.sanitizer)
    implementation(libs.slf4j.api)
    implementation(libs.spring.tx)
    api(libs.mybatis)
    api(libs.mybatis.spring)
    implementation(libs.mybatis.spring.boot.starter)
    api(libs.spring.boot.autoconfigure)
    compileOnlyApi(libs.jetbrains.annotations)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
