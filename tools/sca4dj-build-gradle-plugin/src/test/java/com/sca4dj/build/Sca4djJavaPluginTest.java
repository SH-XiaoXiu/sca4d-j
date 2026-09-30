package com.sca4dj.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 验证应用项目通过 Sca4d-J Java 扩展覆盖 Spotless 字段排序。 */
class Sca4djJavaPluginTest {
    @TempDir
    Path projectDirectory;

    @Test
    @DisplayName("默认顺序由脚手架提供")
    void usesScaffoldOrder() throws IOException {
        prepare("");

        runner("spotlessApply").build();

        String result = Files.readString(javaFile());
        assertTrue(result.indexOf("AService service;") < result.indexOf("ZRepository repository;"));
        runner("spotlessCheck").build();
    }

    @Test
    @DisplayName("应用可替换类型后缀组的优先级")
    void letsAppOverrideOrder() throws IOException {
        prepare("""
                sca4djJava {
                    memberOrderGroups.set(listOf("Repository|Repositories", "Service|Services"))
                }
                """);

        runner("spotlessApply").build();

        String result = Files.readString(javaFile());
        assertTrue(result.indexOf("ZRepository repository;") < result.indexOf("AService service;"));
        runner("spotlessCheck").build();
    }

    private void prepare(String configuration) throws IOException {
        Files.writeString(
                projectDirectory.resolve("settings.gradle.kts"),
                "rootProject.name = \"member-order-fixture\"\n");
        Files.writeString(
                projectDirectory.resolve("gradle.properties"),
                "org.gradle.workers.max=1\norg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=384m\n");
        Files.writeString(projectDirectory.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("com.sca4dj.build.java")
                }
                repositories {
                    mavenCentral()
                }
                """ + configuration);
        Path javaFile = javaFile();
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, """
                class Sample {
                    private final ZRepository repository;
                    private final AService service;

                    Sample(ZRepository repository, AService service) {
                        this.repository = repository;
                        this.service = service;
                    }
                }
                """);
    }

    private Path javaFile() {
        return projectDirectory.resolve("src/main/java/Sample.java");
    }

    private GradleRunner runner(String task) {
        return GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments(task)
                .forwardOutput();
    }
}
