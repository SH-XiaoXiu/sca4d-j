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
    implementation(project(":modules:notification:sca4dj-notification-sms"))
    implementation(libs.aliyun.sms)
    api(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
