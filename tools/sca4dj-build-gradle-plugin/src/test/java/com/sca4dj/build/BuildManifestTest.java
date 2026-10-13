package com.sca4dj.build;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 验证构建入口输出相同事实并跟踪声明变化。 */
class BuildManifestTest {
    @TempDir
    Path directory;

    @Test
    void generatesRuntimeAndJsonMetadataAndTracksVersionChanges() throws Exception {
        Files.writeString(directory.resolve("settings.gradle.kts"), "rootProject.name = \"fixture\"");
        Files.writeString(directory.resolve("build.gradle.kts"), "plugins { id(\"com.sca4dj.build.root\") }");
        Files.writeString(
                directory.resolve("version.properties"),
                "product=fixture\nreleaseVersion=1.2.0\napiContractVersion=2\n");
        runner("generateBuildManifest", "-Prelease", "-Prelease.id=batch-1").build();
        var manifest = new ObjectMapper().readTree(directory.resolve("build/version/build-manifest.json").toFile());
        assertEquals("batch-1", manifest.get("releaseId").asText());
        assertEquals("1.2.0", manifest.get("releaseVersion").asText());
        assertEquals(2, manifest.get("apiContractVersion").asInt());
        var properties = new Properties();
        try (var input = Files.newInputStream(directory.resolve("build/version/META-INF/build-info.properties"))) {
            properties.load(input);
        }
        assertEquals(manifest.get("componentBuildId").asText(), properties.getProperty("build.componentBuildId"));
        runner("generateBuildManifest", "-Prelease.version=1.3.0", "-Prelease.id=batch-2").build();
        manifest = new ObjectMapper().readTree(directory.resolve("build/version/build-manifest.json").toFile());
        assertEquals("1.3.0", manifest.get("releaseVersion").asText());
        assertEquals("dev", manifest.get("buildMode").asText());
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath().withArguments(arguments);
    }
}
