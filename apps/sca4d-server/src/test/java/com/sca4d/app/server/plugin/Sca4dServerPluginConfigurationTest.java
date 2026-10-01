package com.sca4d.app.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** 验证 Server 自定义 YAML 元信息映射与额外配置快照，不解释插件的私有字段。 */
class Sca4dServerPluginConfigurationTest {
    private static final String DOCUMENT = """
            plugin:
              name: SCA4D:SERVER:MFA
              version: 1.0.0
              main: com.sca4d.app.mfa.MfaPolicyPlugin
              api: SCA4D:SERVER
              requires: ">=1.1.0 & <2.0.0"
            policy:
              arbitrary-private-value: [true, 5, custom]
            """;

    @TempDir
    Path temporary;

    @Test
    void mapsAuthorDeclaredMetadataAndProvidesIndependentApplicationSettings() throws Exception {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "first-app");
        var configuration = new Sca4dServerPluginConfiguration(environment);
        Path artifact = artifact(DOCUMENT);
        var first = configuration.load(artifact);
        assertEquals(TreeCode.of("SCA4D:SERVER:MFA"), first.descriptor().id());
        assertEquals("com.sca4d.app.mfa.MfaPolicyPlugin", first.descriptor().entry());
        assertEquals(">=1.1.0 & <2.0.0", first.descriptor().requiresApi());
        assertEquals(Sca4dServerPluginSettings.class, configuration.type());
        environment.setProperty("spring.application.name", "second-app");
        var second = configuration.reload(artifact);
        assertEquals("first-app", first.values().applicationName());
        assertEquals("second-app", second.values().applicationName());
        assertNotSame(first.values(), second.values());
        configuration.unload(first);
        configuration.unload(second);
    }

    @Test
    void rejectsMissingResourceInvalidMetadataAndDuplicateYamlKeys() throws Exception {
        var configuration = new Sca4dServerPluginConfiguration(
                new MockEnvironment().withProperty("spring.application.name", "sca4d-server"));
        Path missing = artifact(null);
        assertThrows(IllegalArgumentException.class, () -> configuration.load(missing));
        Path invalid = artifact("plugin: {name: 42}\n");
        assertThrows(IllegalArgumentException.class, () -> configuration.load(invalid));
        Path duplicate = artifact("plugin: {name: first, name: second}\n");
        assertThrows(IllegalArgumentException.class, () -> configuration.load(duplicate));
    }

    /** 将测试声明写入独立 JAR；空内容表示不包含配置资源。 */
    private Path artifact(String document) throws Exception {
        Path artifact = Files.createTempFile(temporary, "metadata-", ".jar");
        try (var output = new JarOutputStream(Files.newOutputStream(artifact))) {
            if (document != null) {
                output.putNextEntry(new JarEntry("plugin.yml"));
                output.write(document.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return artifact;
    }
}
