import org.apache.tools.ant.filters.ReplaceTokens
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
}

val buildTime = rootProject.extra["sca4dBuildTime"] as String
val buildCommit = rootProject.extra["sca4dBuildCommit"] as String
val buildBranch = rootProject.extra["sca4dBuildBranch"] as String
val buildAuthor = rootProject.extra["sca4dBuildAuthor"] as String
val buildMode = rootProject.extra["sca4dBuildMode"] as String
val buildVersion = rootProject.extra["sca4dBuildVersion"] as String
val applicationTitle = "Sca4d Gateway"
val bannerTokens = mapOf(
    "application.title" to applicationTitle,
    "build.version" to buildVersion,
    "build.time" to buildTime,
    "build.commit" to buildCommit,
    "build.branch" to buildBranch,
    "build.author" to buildAuthor,
    "build.mode" to buildMode,
)

tasks.processResources {
    inputs.properties(bannerTokens)
    filesMatching("banner.txt") {
        filter<ReplaceTokens>("tokens" to bannerTokens)
    }
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("${project.name}-$buildVersion.jar")
    manifest {
        attributes(
            "Implementation-Title" to applicationTitle,
            "Implementation-Version" to buildVersion,
            "Build-Time" to buildTime,
            "Build-Commit" to buildCommit,
            "Build-Branch" to buildBranch,
            "Build-Author" to buildAuthor,
            "Build-Mode" to buildMode,
        )
    }
}

springBoot {
    buildInfo {
        properties {
            name.set(applicationTitle)
            version.set(buildVersion)
            additional.set(
                mapOf(
                    "time" to buildTime,
                    "commit" to buildCommit,
                    "branch" to buildBranch,
                    "author" to buildAuthor,
                    "mode" to buildMode,
                ),
            )
        }
    }
}

extra["springCloudVersion"] = "2025.0.0"

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.0.2")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotless {
    java {
        target("src/**/*.java")
        importOrder("", "java", "javax")
        removeUnusedImports()
        eclipse().configFile("../config/eclipse-java-format.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("*.md", ".gitignore", "config/*.yml", "src/main/resources/*.yml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
