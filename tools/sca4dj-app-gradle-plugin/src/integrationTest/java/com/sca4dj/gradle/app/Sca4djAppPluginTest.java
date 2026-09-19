package com.sca4dj.gradle.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** 验证 App 插件在独立 Gradle 工程中的迁移生成和覆盖保护。 */
class Sca4djAppPluginTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Orders Dependency Fragments And Refuses Overwrite")
    void ordersDependencyFragmentsAndRefusesOverwrite() throws IOException {
        Path repository = temporaryDirectory.resolve("repository");
        publishFixture(repository, "base", null, "CREATE TABLE base_item (id BIGINT PRIMARY KEY);\n");
        publishFixture(repository, "feature", "base", "CREATE TABLE feature_item (id BIGINT PRIMARY KEY);\n");

        Path application = temporaryDirectory.resolve("application");
        Files.createDirectories(application);
        Files.writeString(application.resolve("settings.gradle.kts"), "rootProject.name = \"sample-app\"\n");
        Files.writeString(application.resolve("app-schema.sql"),
                "CREATE TABLE app_profile (id BIGINT PRIMARY KEY);\n");
        Files.writeString(application.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("com.sca4dj.app")
                }

                repositories {
                    maven { url = uri("%s") }
                }

                dependencies {
                    runtimeOnly("fixture:feature:1.0")
                }

                sca4djApp {
                    migrationName.set("sample_app")
                    schemaFragment.set(layout.projectDirectory.file("app-schema.sql"))
                }
                """.formatted(repository.toUri()));

        BuildResult result = runner(application).withArguments("initFlyway", "--stacktrace").build();
        Path migration = application.resolve("src/main/resources/db/migration/V1__init_sample_app.sql");
        String sql = Files.readString(migration);
        assertTrue(result.getOutput().contains("已生成 src/main/resources/db/migration/V1__init_sample_app.sql"));
        assertTrue(sql.indexOf("CREATE TABLE base_item") < sql.indexOf("CREATE TABLE feature_item"));
        assertTrue(sql.indexOf("CREATE TABLE feature_item") < sql.indexOf("CREATE TABLE app_profile"));

        try {
            runner(application).withArguments("initFlyway", "--stacktrace").build();
            throw new AssertionError("已有迁移时 initFlyway 应失败");
        } catch (UnexpectedBuildFailure exception) {
            assertTrue(exception.getBuildResult().getOutput().contains("初始化任务不会覆盖"));
        }
    }

    @Test
    @DisplayName("Rejects Invalid Migration Name Before Resolving Fragments")
    void rejectsInvalidMigrationNameBeforeResolvingFragments() throws IOException {
        Path application = temporaryDirectory.resolve("invalid-name");
        Files.createDirectories(application);
        Files.writeString(application.resolve("settings.gradle.kts"), "rootProject.name = \"invalid-name\"\n");
        Files.writeString(application.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("com.sca4dj.app")
                }

                sca4djApp {
                    migrationName.set("../outside")
                }
                """);

        try {
            runner(application).withArguments("initFlyway", "--stacktrace").build();
            throw new AssertionError("非法 migrationName 应失败");
        } catch (UnexpectedBuildFailure exception) {
            assertTrue(exception.getBuildResult().getOutput().contains("migrationName 只能使用"));
        }
    }

    /** 创建包含一个结构片段的 Maven 测试构件。 */
    private static void publishFixture(Path repository, String artifact, String dependency, String schema)
            throws IOException {
        Path artifactDirectory = repository.resolve("fixture").resolve(artifact).resolve("1.0");
        Files.createDirectories(artifactDirectory);
        String dependencies = dependency == null ? "" : """
                <dependencies>
                  <dependency>
                    <groupId>fixture</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0</version>
                    <scope>runtime</scope>
                  </dependency>
                </dependencies>
                """.formatted(dependency);
        Files.writeString(artifactDirectory.resolve(artifact + "-1.0.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId>
                  <artifactId>%s</artifactId>
                  <version>1.0</version>
                  %s
                </project>
                """.formatted(artifact, dependencies));
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(artifactDirectory.resolve(artifact + "-1.0.jar")))) {
            output.putNextEntry(new JarEntry("META-INF/sca4dj/schema/" + artifact + ".sql"));
            output.write(schema.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    /** 创建带插件测试类路径的 GradleRunner。 */
    private static GradleRunner runner(Path projectDirectory) {
        return GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDirectory.toFile())
                .forwardOutput();
    }
}
