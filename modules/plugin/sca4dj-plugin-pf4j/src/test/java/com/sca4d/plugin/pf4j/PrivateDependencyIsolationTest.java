package com.sca4d.plugin.pf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.plugin.PluginContext;
import com.sca4d.core.api.plugin.PluginDescriptor;
import com.sca4d.core.api.plugin.PluginEntrypoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.ToolProvider;

/** 用分别编译的真实 JAR 验证同名私有库隔离和宿主隐式回退禁止。 */
public class PrivateDependencyIsolationTest {
    @TempDir
    Path temporary;

    private final Pf4jPluginLoader loader = new Pf4jPluginLoader();

    @Test
    @DisplayName("两个插件分别加载同名私有库的不同版本")
    void isolatesPrivateVersions() throws Exception {
        try (var first = loader.load(artifact("a", "version-a", false), descriptor(), Entry.class, shared());
                var second = loader.load(artifact("b", "version-b", false), descriptor(), Entry.class, shared())) {
            assertEquals("version-a", ((Entry) first.entrypoint()).value());
            assertEquals("version-b", ((Entry) second.entrypoint()).value());
            assertNotSame(
                    first.classLoader().loadClass("library.Codec"),
                    second.classLoader().loadClass("library.Codec"));
            assertEquals(Entry.class, first.classLoader().loadClass(Entry.class.getName()));
        }
    }

    @Test
    @DisplayName("插件找不到依赖时不能借用宿主未公开实现")
    void rejectsImplicitHostDependency() throws Exception {
        try (var plugin = loader.load(
                artifact("private-host", "ignored", true),
                descriptor(),
                Entry.class,
                shared())) {
            assertThrows(NoClassDefFoundError.class, () -> ((Entry) plugin.entrypoint()).value());
        }
    }

    @Test
    @DisplayName("同名 YAML 资源只来自各自插件，不读取宿主或其他代次的资源")
    void isolatesEmbeddedConfigurationResources() throws Exception {
        try (var first = loader.load(artifact("resource-a", "version-a", false), descriptor(), Entry.class, shared());
                var second = loader
                        .load(artifact("resource-b", "version-b", false), descriptor(), Entry.class, shared());
                var firstInput = first.entrypoint().getClass().getResourceAsStream("/plugin.yml");
                var secondInput = second.entrypoint().getClass().getResourceAsStream("/plugin.yml")) {
            assertEquals("label: version-a\n", new String(firstInput.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("label: version-b\n", new String(secondInput.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(1, java.util.Collections.list(first.classLoader().getResources("plugin.yml")).size());
        }
    }

    @Test
    @DisplayName("重复打包共享基类在入口构造前失败")
    void rejectsDuplicateSharedBase() throws Exception {
        Path duplicate = temporary.resolve("duplicate.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(duplicate))) {
            output.putNextEntry(new JarEntry(Entry.class.getName().replace('.', '/') + ".class"));
            output.write(new byte[] {0});
            output.closeEntry();
        }
        assertThrows(IllegalArgumentException.class, () -> loader.load(duplicate, descriptor(), Entry.class, shared()));
    }

    /** 分别编译插件和私有库，编译可见的宿主内部类不自动成为运行共享类。 */
    private Path artifact(String name, String version, boolean usesHost) throws Exception {
        Path root = temporary.resolve(name);
        Files.createDirectories(root);
        Path codec = root.resolve("Codec.java");
        Files.writeString(
                codec,
                "package library; public final class Codec { public String value() { return \""
                        + version + "\"; } }");
        Path entry = root.resolve("Candidate.java");
        String body = usesHost
                ? "return new com.sca4d.plugin.pf4j.PrivateDependencyIsolationTest.HostPrivate().value();"
                : "return new library.Codec().value();";
        Files.writeString(entry, """
                package fixture;
                public final class Candidate extends com.sca4d.plugin.pf4j.PrivateDependencyIsolationTest.Entry {
                    @Override public String value() { %s }
                    @Override public void onLoad(com.sca4d.core.api.plugin.PluginContext context) {}
                    @Override public void onUnload() {}
                }
                """.formatted(body));
        Path classes = root.resolve("classes");
        Files.createDirectories(classes);
        String classpath = Path.of(Entry.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + File.pathSeparator
                + Path.of(PluginEntrypoint.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var compiler = ToolProvider.getSystemJavaCompiler();
        StringWriter diagnostics = new StringWriter();
        try (var manager = compiler.getStandardFileManager(null, null, null)) {
            assertTrue(
                    compiler.getTask(
                            diagnostics,
                            manager,
                            null,
                            List.of(
                                    "-classpath",
                                    classpath,
                                    "-d",
                                    classes.toString()),
                            null,
                            manager.getJavaFileObjects(entry.toFile(), codec.toFile())).call(),
                    diagnostics.toString());
        }
        Path jar = root.resolve("plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
                var paths = Files.walk(classes)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace(File.separatorChar, '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
            output.putNextEntry(new JarEntry("plugin.yml"));
            output.write(("label: " + version + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }

    /** 返回测试 App 明确共享的类型，宿主内部类不在清单中。 */
    private Set<String> shared() {
        return Set.of(
                Entry.class.getName(),
                PrivateDependencyIsolationTest.class.getName(),
                PluginEntrypoint.class.getName(),
                PluginContext.class.getName());
    }

    /** 创建不依赖测试顺序的固定插件描述。 */
    private PluginDescriptor descriptor() {
        return new PluginDescriptor(TreeCode.of("TEST:PLUGIN"), "1.0.0", TreeCode.of("TEST"), "*", "fixture.Candidate");
    }

    /** 表达测试 App 自己的插件基类，具体实现由私有加载器加载。 */
    public abstract static class Entry implements PluginEntrypoint {
        /** 返回插件私有库产生的结果。 */
        public abstract String value();
    }

    /** 表示宿主没有公开给插件的内部类。 */
    public static class HostPrivate {
        /** 返回内部实现结果。 */
        public String value() {
            return "host";
        }
    }
}
