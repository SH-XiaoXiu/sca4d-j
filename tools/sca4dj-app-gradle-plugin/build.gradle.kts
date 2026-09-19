plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.sca4dj.build.java")
}

group = "com.sca4dj"
version = providers.gradleProperty("sca4dj.version").orElse("0.0.1-alpha").get()

repositories {
    mavenCentral()
}

dependencies {
    add("integrationTestImplementation", gradleTestKit())
    add("integrationTestImplementation", libs.junit.jupiter)
    add("integrationTestRuntimeOnly", libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("sca4djApp") {
            id = "com.sca4dj.app"
            implementationClass = "com.sca4dj.gradle.app.Sca4djAppPlugin"
            displayName = "Sca4d-J App Plugin"
            description = "生成 Sca4d-J App 自己负责的 Flyway 初始迁移"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "sca4dj"
            url =
                uri(
                    providers
                        .gradleProperty("sca4dj.repository")
                        .orElse(layout.buildDirectory.dir("repository").map { it.asFile.toURI().toString() })
                        .get(),
                )
        }
    }
}

sca4djBuild {
    minimumLevel.set(com.sca4dj.build.QualityLevel.OBSESSIVE)
}
