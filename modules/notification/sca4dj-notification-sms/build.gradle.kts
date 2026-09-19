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
    implementation(project(":modules:notification:sca4dj-notification"))
    api(libs.mybatis)
    api(libs.mybatis.spring)
    implementation(libs.mybatis.spring.boot.starter)
    implementation(libs.slf4j.api)
    api(libs.spring.boot.autoconfigure)
    implementation(libs.spring.tx)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
