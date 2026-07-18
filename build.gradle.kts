import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("org.springframework.boot") version "3.5.10" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.diffplug.spotless") version "7.0.2" apply false
}

allprojects {
    group = "com.sca4dj"
    version = "0.0.1-alpha"
}

fun gitValue(vararg arguments: String): String {
    return providers.exec {
        commandLine("git", *arguments)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}

val buildTime = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
val buildCommit = gitValue("rev-parse", "--short", "HEAD").ifBlank { "dev" }
val buildBranch = gitValue("branch", "--show-current").ifBlank { "detached" }
val buildAuthor = gitValue("log", "-1", "--format=%an").ifBlank { "unknown" }
val latestTag = gitValue("describe", "--tags", "--abbrev=0")
val releaseRequested = providers.gradleProperty("release").isPresent
val releaseBase = providers.gradleProperty("release.version").orNull?.takeIf(String::isNotBlank)
    ?: latestTag.takeIf(String::isNotBlank)
    ?: "untagged"
val buildMode = if (releaseRequested) "release" else "dev"
val buildVersion = if (releaseRequested) "$releaseBase-$buildCommit" else "$buildCommit-$buildTime"

extra["sca4dBuildTime"] = buildTime
extra["sca4dBuildCommit"] = buildCommit
extra["sca4dBuildBranch"] = buildBranch
extra["sca4dBuildAuthor"] = buildAuthor
extra["sca4dBuildMode"] = buildMode
extra["sca4dBuildVersion"] = buildVersion

logger.lifecycle("Building sca4d-j version: $buildVersion ($buildMode)")

val environmentContractFiles = listOf(
    file("docker-compose.yml"),
    file("sca4d-server/src/main/resources/application.yml"),
    file("sca4d-server/src/main/java/com/sca4d/config/RedisPasswordEnvironmentPostProcessor.java"),
    file("sca4d-gateway/src/main/resources/application.yml"),
    file("sca4d-admin/scripts/check-openapi.mjs"),
    file("sca4d-admin/app/vite.config.ts"),
    file("sca4d-admin/app/playwright.config.ts"),
    file("sca4d-admin/app/e2e/admin.spec.ts"),
    file("sca4d-admin/app/e2e/passkey.real.spec.ts"),
    file("sca4d-admin/app/src/api/client.ts"),
)

val checkEnvExample by tasks.registering {
    group = "verification"
    description = "校验 .env.example 覆盖全部运行配置且每项具有参数说明"
    inputs.file(file(".env.example"))
    inputs.files(environmentContractFiles)

    doLast {
        // 只识别 ${NAME} 和 ${NAME:default}；TypeScript 的 ${String(...)} 不是环境变量入口。
        val placeholderPattern = Regex("""\$\{([A-Z][A-Z0-9_]*)(?=[:}])""")
        val nodeEnvironmentPattern = Regex("""(?:process|import\.meta)\.env\.([A-Z][A-Z0-9_]*)""")
        val directEnvironmentPattern = Regex("""[\"'](SCA4DJ_[A-Z0-9_]+)[\"']""")
        val requiredKeys = environmentContractFiles
            .flatMap { contractFile ->
                val content = contractFile.readText()
                placeholderPattern.findAll(content).map { it.groupValues[1] }.toList() +
                    nodeEnvironmentPattern.findAll(content).map { it.groupValues[1] }.toList() +
                    directEnvironmentPattern.findAll(content).map { it.groupValues[1] }.toList()
            }
            .toSortedSet()

        val exampleLines = file(".env.example").readLines()
        val assignmentPattern = Regex("""^([A-Z][A-Z0-9_]*)=.*$""")
        val assignments = exampleLines.mapIndexedNotNull { index, line ->
            assignmentPattern.matchEntire(line)?.groupValues?.get(1)?.let { it to index }
        }
        val duplicateKeys = assignments
            .groupingBy { it.first }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        if (duplicateKeys.isNotEmpty()) {
            throw GradleException(".env.example 存在重复配置：${duplicateKeys.joinToString()}")
        }

        val exampleKeys = assignments.map { it.first }.toSortedSet()
        val missingKeys = requiredKeys - exampleKeys
        if (missingKeys.isNotEmpty()) {
            throw GradleException(".env.example 缺少运行配置：${missingKeys.joinToString()}")
        }
        val staleKeys = exampleKeys - requiredKeys
        if (staleKeys.isNotEmpty()) {
            throw GradleException(".env.example 存在无运行入口配置：${staleKeys.joinToString()}")
        }

        val undocumentedKeys = assignments.mapNotNull { (key, index) ->
            val comment = exampleLines.subList(0, index)
                .asReversed()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
            if (comment == null || !comment.startsWith("#") || comment.removePrefix("#").trim().all { it == '-' }) {
                key
            } else {
                null
            }
        }
        if (undocumentedKeys.isNotEmpty()) {
            throw GradleException(".env.example 配置缺少参数预期说明：${undocumentedKeys.joinToString()}")
        }
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.compilerArgs.add("-parameters")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        tasks.named("check") {
            dependsOn(checkEnvExample)
        }
    }
}
