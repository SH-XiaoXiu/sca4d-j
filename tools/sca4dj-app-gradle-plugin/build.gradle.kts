plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.sca4dj.build.java")
}

group = "com.sca4dj"
version = providers.gradleProperty("sca4dj.version").orElse("0.0.1-alpha").get()

val pluginProcessor = sourceSets.create("pluginProcessor")
val pluginProcessorJar = tasks.register<Jar>("pluginProcessorJar") {
    group = "build"
    description = "构建 App 插件 API 注解处理器"
    archiveClassifier.set("plugin-processor")
    archiveFileName.set("sca4dj-plugin-processor.jar")
    from(pluginProcessor.output)
}

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
        create("sca4djPluginApi") {
            id = "com.sca4dj.plugin-api"
            implementationClass = "com.sca4dj.gradle.app.PluginApiGradlePlugin"
            displayName = "Sca4d-J App Plugin API"
            description = "生成 App 插件 API 构件和共享类型描述"
        }
        create("sca4djPlugin") {
            id = "com.sca4dj.plugin"
            implementationClass = "com.sca4dj.gradle.app.PluginArtifactGradlePlugin"
            displayName = "Sca4d-J Plugin Artifact"
            description = "生成插件交付描述并打包私有依赖"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("pluginProcessor") {
            artifact(pluginProcessorJar) { classifier = null }
            artifactId = "sca4dj-plugin-processor"
        }
    }
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
