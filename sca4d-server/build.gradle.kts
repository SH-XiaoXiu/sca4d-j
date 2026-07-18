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
val applicationTitle = "Sca4d Server"
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
            version.set(project.version.toString())
            additional.set(
                mapOf(
                    "buildVersion" to buildVersion,
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

dependencies {
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation("cn.dev33:sa-token-spring-boot3-starter:1.42.0")
    implementation("cn.dev33:sa-token-redis-template:1.42.0")
    implementation("cn.hutool:hutool-all:5.8.38")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.14.0")
    implementation("com.bucket4j:bucket4j_jdk17-redisson:8.14.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.2")
    implementation("com.github.spullara.mustache.java:compiler:0.9.14")
    implementation("com.cedarpolicy:cedar-java:4.10.0:uber")
    implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20240325.1")
    implementation("com.google.zxing:javase:3.5.4")
    implementation("com.jayway.jsonpath:json-path")
    implementation("com.nimbusds:oauth2-oidc-sdk:11.37.2")
    implementation("com.warrenstrange:googleauth:1.5.0")
    implementation("com.yubico:webauthn-server-core:2.9.0")
    implementation("org.flowable:flowable-spring-boot-starter-process:7.2.0")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.springframework.ldap:spring-ldap-core")
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
    implementation("io.minio:minio:8.5.17")
    implementation("org.mybatis:mybatis:3.5.19")
    implementation("org.redisson:redisson-spring-boot-starter:3.49.0")
    implementation("io.projectreactor.netty:reactor-netty-http")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("com.mybatis-flex:mybatis-flex-spring-boot3-starter:1.11.5")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.unboundid:unboundid-ldapsdk:7.0.4")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotless {
    java {
        target("src/**/*.java")
        targetExclude("src/main/resources/db/migration/**")
        importOrder("", "java", "javax")
        removeUnusedImports()
        eclipse().configFile("../config/eclipse-java-format.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("*.md", ".gitignore", ".env.example")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
