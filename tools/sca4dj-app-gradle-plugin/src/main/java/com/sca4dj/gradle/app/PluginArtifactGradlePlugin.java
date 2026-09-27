package com.sca4dj.gradle.app;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.tasks.Jar;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/** 为插件登记 API 编译依赖和包含私有依赖的普通 JAR 交付任务。 */
public final class PluginArtifactGradlePlugin implements Plugin<Project> {
    /** API 仅用于编译与测试，交付描述写入构建目录。 */
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java-library");
        PluginArtifactExtension extension = project.getExtensions().create("sca4djPlugin", PluginArtifactExtension.class);
        // API 只进入编译和测试依赖，插件运行构件使用宿主加载的共享定义。
        Configuration api = project.getConfigurations().create("pluginApi");
        api.setCanBeConsumed(false);
        project.getConfigurations().getByName("compileOnly").extendsFrom(api);
        project.getConfigurations().getByName("testImplementation").extendsFrom(api);
        var main = project.getExtensions().getByType(SourceSetContainer.class).getByName("main");
        // 把插件身份、入口和版本要求写入独立描述，加载时先校验再实例化入口。
        var metadata = project.getTasks().register("pluginMetadata", task -> {
            task.getInputs().property("pluginId", extension.getId());
            task.getInputs().property("pluginEntry", extension.getEntryClass());
            task.getInputs().property("pluginApi", extension.getApi());
            task.getInputs().property("requiresApi", extension.getRequiresApi());
            task.getInputs().property("version", project.provider(() -> project.getVersion().toString()));
            task.getOutputs().dir(project.getLayout().getBuildDirectory().dir("generated/plugin-metadata"));
            task.doLast(ignored -> {
                try {
                    Path path = project.getLayout().getBuildDirectory().dir("generated/plugin-metadata").get()
                            .getAsFile().toPath().resolve("META-INF/sca4dj/plugin.properties");
                    Files.createDirectories(path.getParent());
                    Properties properties = new Properties();
                    properties.setProperty("plugin.id", extension.getId().get());
                    properties.setProperty("plugin.version", project.getVersion().toString());
                    properties.setProperty("plugin.api", extension.getApi().get());
                    properties.setProperty("plugin.requires-api", extension.getRequiresApi().get());
                    properties.setProperty("plugin.entry", extension.getEntryClass().get());
                    try (OutputStream output = Files.newOutputStream(path)) {
                        properties.store(output, "Generated plugin artifact");
                    }
                } catch (IOException failure) {
                    throw new GradleException("插件交付描述生成失败", failure);
                }
            });
        });
        // 私有依赖合并到普通 JAR；重复类和资源直接失败，不能依赖覆盖顺序。
        project.getTasks().register("pluginJar", Jar.class, task -> {
            task.dependsOn(project.getTasks().named("classes"), metadata);
            task.getArchiveClassifier().set("plugin");
            task.setDuplicatesStrategy(DuplicatesStrategy.FAIL);
            task.from(main.getOutput());
            task.from(project.getLayout().getBuildDirectory().dir("generated/plugin-metadata"));
            Configuration runtime = project.getConfigurations().getByName("runtimeClasspath");
            task.from(project.provider(() -> runtime.getFiles().stream().map(project::zipTree).toList()),
                    copy -> copy.exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA",
                            "META-INF/*.DSA", "module-info.class", "META-INF/versions/**"));
            task.doFirst(ignored -> {
                try {
                    // 按生成 API 的共享清单检查依赖，防止插件再次打包宿主类型。
                    Set<String> shared = new HashSet<>();
                    for (var file : api) {
                        try (JarFile jar = new JarFile(file)) {
                            Properties description = new Properties();
                            var descriptionEntry = jar.getJarEntry("META-INF/sca4dj/api.properties");
                            if (descriptionEntry == null) continue;
                            try (var input = jar.getInputStream(descriptionEntry)) { description.load(input); }
                            var index = jar.getJarEntry(description.getProperty("api.shared-index"));
                            if (index != null) {
                                try (Scanner scanner = new Scanner(jar.getInputStream(index), java.nio.charset.StandardCharsets.UTF_8)) {
                                    while (scanner.hasNextLine()) shared.add(scanner.nextLine());
                                }
                            }
                        }
                    }
                    for (var file : runtime) {
                        try (JarFile jar = new JarFile(file)) {
                            boolean duplicate = jar.stream().map(ZipEntry::getName).filter(n -> n.endsWith(".class"))
                                    .map(n -> n.substring(0, n.length() - 6).replace('/', '.')).anyMatch(shared::contains);
                            if (duplicate) throw new GradleException("插件私有依赖重复打包共享类型: " + file.getName());
                        }
                    }
                } catch (IOException failure) {
                    throw new GradleException("插件交付类型校验失败", failure);
                }
            });
        });
    }
}
