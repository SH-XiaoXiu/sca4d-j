import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.javadoc.Javadoc

plugins {
    id("com.sca4dj.build.root")
    alias(libs.plugins.spotless)
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

val rootMarkdownFiles =
    files(projectDir.listFiles { file -> file.isFile && file.extension == "md" } ?: emptyArray<File>())
val docsMarkdownFiles =
    files(file("docs").listFiles { file -> file.isFile && file.extension == "md" } ?: emptyArray<File>())
val rootConfigurationFiles =
    files(
        projectDir.listFiles { file ->
            file.isFile && file.extension in setOf("yml", "yaml", "json")
        } ?: emptyArray<File>(),
    )

spotless {
    kotlinGradle {
        target("build.gradle.kts", "settings.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("rootMarkdown") {
        target(
            rootMarkdownFiles,
            docsMarkdownFiles,
            fileTree("docs/home") { include("**/*.md") },
            fileTree("docs/reference") { include("**/*.md") },
            fileTree("docs/tutorials") { include("**/*.md") },
            fileTree("docs/standards") { include("**/*.md") },
            fileTree("docs/requirements") { include("**/*.md") },
            fileTree("docs/ai-collaboration") { include("**/*.md") },
            fileTree(".github") { include("**/*.md") },
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("rootConfiguration") {
        target(
            rootConfigurationFiles,
            fileTree(".github") { include("**/*.yml", "**/*.yaml") },
            fileTree("config") { include("**/*.xml") },
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

sca4djBuild {
    minimumLevel.set(com.sca4dj.build.QualityLevel.OBSESSIVE)
    format(tasks.named("spotlessApply"))
    formatCheck(tasks.named("spotlessCheck"))
}

val vitePressOutput = layout.projectDirectory.dir("docs/.vitepress/dist")
val collectedJavadoc = layout.projectDirectory.dir("docs/public/javadoc")
val documentationSiteOutput = layout.buildDirectory.dir("docs/site")

val buildVitePress =
    tasks.register<Exec>("buildVitePress") {
        group = "documentation"
        description = "构建 Sca4d-J VitePress 文档"
        workingDir(layout.projectDirectory.dir("docs"))
        commandLine("pnpm", "run", "docs:build")
        inputs.files(
            fileTree("docs") {
                exclude("node_modules/**", ".vitepress/cache/**", ".vitepress/dist/**", "old/**")
            },
        )
        outputs.dir(vitePressOutput)
    }

val collectJavadoc =
    tasks.register<Sync>("collectJavadoc") {
        group = "documentation"
        description = "按 Gradle 项目路径汇总标准 Javadoc"
        into(collectedJavadoc)
    }

subprojects {
    pluginManager.withPlugin("java") {
        val projectJavadoc = tasks.named<Javadoc>("javadoc")
        val javadocPath = path.removePrefix(":").replace(':', '/')
        collectJavadoc.configure {
            dependsOn(projectJavadoc)
            from(projectJavadoc.map { requireNotNull(it.destinationDir) }) {
                into(javadocPath)
            }
        }
    }
}

val includedJavadocs =
    mapOf(
        "tools/sca4dj-build-gradle-plugin" to
            gradle.includedBuild("sca4dj-build-gradle-plugin").task(":javadoc"),
        "tools/sca4dj-app-gradle-plugin" to
            gradle.includedBuild("sca4dj-app-gradle-plugin").task(":javadoc"),
    )

tasks.named("javadoc") {
    dependsOn(includedJavadocs.values)
}

collectJavadoc.configure {
    dependsOn(includedJavadocs.values)
    includedJavadocs.keys.forEach { projectPath ->
        from(layout.projectDirectory.dir("$projectPath/build/docs/javadoc")) {
            into(projectPath)
        }
    }
}

buildVitePress.configure {
    dependsOn(collectJavadoc)
}

tasks.register<Sync>("documentationSite") {
    group = "documentation"
    description = "构建包含 VitePress 和分项目 Javadoc 的完整文档站"
    dependsOn(buildVitePress)
    into(documentationSiteOutput)
    from(vitePressOutput)
}
