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
    implementation(project(":modules:plugin:sca4dj-plugin"))
    implementation(project(":modules:plugin:sca4dj-plugin-pf4j"))
    implementation(project(":core:sca4dj-core-spring"))
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.tx)
    implementation(libs.spring.aspectjweaver)
    implementation(libs.slf4j.api)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
