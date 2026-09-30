import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-gradle-plugin`
    checkstyle
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotbugs)
}

spotless {
    java {
        target("src/**/*.java")
        importOrder("", "java", "javax")
        removeUnusedImports()
        eclipse().configFile(layout.projectDirectory.file("src/main/resources/com/sca4dj/quality/eclipse-java-format.xml"))
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

checkstyle {
    toolVersion = "10.26.1"
    config =
        resources.text.fromFile(
            layout.projectDirectory.file("src/main/resources/com/sca4dj/quality/checkstyle.xml"),
        )
    isIgnoreFailures = true
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
        sarif.required.set(true)
    }
}

spotbugs {
    ignoreFailures.set(true)
    showProgress.set(true)
    excludeFilter.set(layout.projectDirectory.file("src/main/resources/com/sca4dj/quality/spotbugs-exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.maybeCreate("xml").required.set(true)
    reports.maybeCreate("sarif").required.set(true)
}

group = "com.sca4dj.build"
version = "0.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.spotbugs.gradle.plugin)
    implementation(libs.jackson.databind)

    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("sca4djJava") {
            id = "com.sca4dj.build.java"
            implementationClass = "com.sca4dj.build.Sca4djJavaPlugin"
            displayName = "Sca4d-J Java Conventions"
            description = "配置 Sca4d-J Java 编译、测试、格式化和静态检查约定"
        }
        create("sca4djMapping") {
            id = "com.sca4dj.build.mapping"
            implementationClass = "com.sca4dj.build.Sca4djBuildMappingPlugin"
            displayName = "Sca4d-J Build Mapping"
            description = "把当前 Gradle 项目的任务和报告映射到统一构建阶段"
        }
        create("sca4djAggregate") {
            id = "com.sca4dj.build.aggregate"
            implementationClass = "com.sca4dj.build.Sca4djBuildAggregatePlugin"
            displayName = "Sca4d-J Hierarchical Build Aggregate"
            description = "只聚合当前 Gradle 项目的直接子项目"
        }
        create("sca4djPublish") {
            id = "com.sca4dj.build.publish"
            implementationClass = "com.sca4dj.build.Sca4djPublishingPlugin"
            displayName = "Sca4d-J Publishing Conventions"
            description = "配置 Sca4d-J 公共构件发布约定"
        }
        create("sca4djRoot") {
            id = "com.sca4dj.build.root"
            implementationClass = "com.sca4dj.build.Sca4djRootPlugin"
            displayName = "Sca4d-J Repository Build"
            description = "提供 Sca4d-J 仓库级编译、测试、构建、格式化和审计任务"
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all,-missing", true)
        addBooleanOption("Werror", true)
    }
}

val selfMemberOrderApply =
    tasks.register<JavaExec>("selfMemberOrderApply") {
        group = "formatting"
        description = "按脚手架固定规则整理构建插件自己的 Java 字段"
        dependsOn(tasks.named("classes"))
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("com.sca4dj.build.JavaMemberOrderTool")
        args(
            "apply",
            layout.projectDirectory
                .dir("src")
                .asFile.absolutePath,
        )
    }

val selfMemberOrderCheck =
    tasks.register<JavaExec>("selfMemberOrderCheck") {
        group = "verification"
        description = "检查构建插件自己的 Java 字段顺序"
        dependsOn(tasks.named("classes"))
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("com.sca4dj.build.JavaMemberOrderTool")
        args(
            "check",
            layout.projectDirectory
                .dir("src")
                .asFile.absolutePath,
        )
    }

tasks.named("spotlessJavaApply") {
    dependsOn(selfMemberOrderApply)
}
tasks.named("spotlessJavaCheck") {
    dependsOn(selfMemberOrderCheck)
}
