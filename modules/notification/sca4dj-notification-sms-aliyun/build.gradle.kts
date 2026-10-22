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
    implementation(libs.aliyun.sms) {
        exclude(group = "xpp3", module = "xpp3")
        // dom4j 的可选解析器会注册全局 SAX 工厂，且不支持 Logback 的 XML 安全选项；使用 JDK 解析器。
        exclude(group = "pull-parser", module = "pull-parser")
    }
    api(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
