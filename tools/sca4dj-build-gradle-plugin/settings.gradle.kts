pluginManagement {
    repositories {
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
    repositories {
        maven(url = "https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "sca4dj-build-gradle-plugin"
