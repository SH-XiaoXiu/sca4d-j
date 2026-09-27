package com.sca4dj.gradle.app;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import groovy.util.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;

/** 为 App 注册代码生成、可发布 API 构件和精确共享类索引。 */
public final class PluginApiGradlePlugin implements Plugin<Project> {

    /** 把声明处理器接入编译，导出清单在编译后用于构件过滤。 */
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java");
        project.getPluginManager().apply("idea");
        project.getPluginManager().apply("maven-publish");
        PluginApiExtension extension = project.getExtensions().create("appPluginApi", PluginApiExtension.class);
        extension.getVersion().convention("1.0.0");
        // 共享依赖由 App 显式选择；处理器只参与编译，不进入宿主运行依赖。
        Configuration shared = project.getConfigurations().create("pluginShared");
        shared.setTransitive(false);
        shared.setCanBeConsumed(false);
        Configuration processor = project.getConfigurations().create("pluginProcessor");
        processor.setCanBeConsumed(false);
        project.getConfigurations().getByName("annotationProcessor").extendsFrom(processor);
        SourceSet main = project.getExtensions().getByType(SourceSetContainer.class).getByName("main");
        var compile = project.getTasks().named(main.getCompileJavaTaskName(), JavaCompile.class);
        var generated = project.getLayout().getBuildDirectory().dir("generated/sources/annotationProcessor/java/main");
        compile.configure(task -> {
            task.getOptions().getCompilerArgumentProviders().add(new PluginApiCompilerArguments(extension, shared));
            task.getOptions().getGeneratedSourceOutputDirectory().set(generated);
        });
        IdeaModel idea = project.getExtensions().getByType(IdeaModel.class);
        idea.getModule().getSourceDirs().add(generated.get().getAsFile());
        idea.getModule().getGeneratedSourceDirs().add(generated.get().getAsFile());
        // 编译后合并导出类与显式共享构件的类名，运行时据此限制加载来源。
        var index = project.getTasks().register("pluginApiIndex", task -> {
            task.dependsOn(compile);
            task.getInputs().files(main.getOutput().getClassesDirs(), shared);
            task.getInputs().property("apiId", extension.getId());
            task.getOutputs().dir(project.getLayout().getBuildDirectory().dir("generated/plugin-api-index"));
            task.doLast(ignored -> {
                try {
                    Set<String> names = new TreeSet<>();
                    for (var directory : main.getOutput().getClassesDirs()) {
                        Path root = directory.toPath();
                        if (!Files.exists(root)) continue;
                        Set<String> exported = exports(root);
                        try (var paths = Files.walk(root)) {
                            paths.filter(path -> path.toString().endsWith(".class")).forEach(path -> {
                                String name = root.relativize(path).toString().replace(java.io.File.separatorChar, '.');
                                name = name.substring(0, name.length() - 6);
                                if (matches(name, exported)) names.add(name);
                            });
                        }
                    }
                    for (var file : shared) {
                        try (JarFile jar = new JarFile(file)) {
                            jar.stream().map(entry -> entry.getName()).filter(name -> name.endsWith(".class")
                                    && !name.equals("module-info.class") && !name.startsWith("META-INF/"))
                                    .map(name -> name.substring(0, name.length() - 6).replace('/', '.'))
                                    .forEach(names::add);
                        }
                    }
                    Path output = project.getLayout().getBuildDirectory().dir("generated/plugin-api-index")
                            .get().getAsFile().toPath().resolve("META-INF/sca4dj/api-shared-"
                                    + extension.getId().get().replace(':', '_').replace('/', '_').replace('.', '_')
                                    .toUpperCase(java.util.Locale.ROOT) + ".list");
                    Files.createDirectories(output.getParent());
                    Files.write(output, names);
                } catch (IOException failure) {
                    throw new GradleException("App API 共享类索引生成失败", failure);
                }
            });
        });
        // API JAR 只包含导出清单中的类及嵌套类，不能打包整个 App 输出。
        var apiJar = project.getTasks().register("pluginApiJar", Jar.class, task -> {
            task.dependsOn(index);
            task.getArchiveClassifier().set("plugin-api");
            task.from(main.getOutput().getClassesDirs(), copy -> copy.include(details -> {
                if (details.isDirectory()) return true;
                String path = details.getPath();
                if (path.startsWith("META-INF/sca4dj/api")) return true;
                if (!path.endsWith(".class")) return false;
                String name = path.substring(0, path.length() - 6).replace('/', '.');
                for (var directory : main.getOutput().getClassesDirs()) {
                    if (matches(name, exports(directory.toPath()))) return true;
                }
                return false;
            }));
            task.from(project.getLayout().getBuildDirectory().dir("generated/plugin-api-index"));
        });
        Configuration elements = project.getConfigurations().create("pluginApiElements");
        elements.setCanBeResolved(false);
        elements.setCanBeConsumed(true);
        elements.extendsFrom(shared);
        elements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
        elements.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
        elements.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
        elements.getOutgoing().artifact(apiJar);
        var sources = project.getTasks().register("pluginApiSourcesJar", Jar.class, task -> {
            task.dependsOn(compile);
            task.getArchiveClassifier().set("plugin-api-sources");
            task.from(main.getAllJava());
            task.from(generated);
            task.include(details -> {
                if (details.isDirectory()) return true;
                String path = details.getPath();
                if (!path.endsWith(".java")) return false;
                String name = path.substring(0, path.length() - 5).replace('/', '.');
                return main.getOutput().getClassesDirs().getFiles().stream()
                        .anyMatch(directory -> matches(name, exports(directory.toPath())));
            });
        });
        // 发布 API 和对应源码，POM 记录插件编译时需要的共享依赖。
        project.afterEvaluate(ignored -> {
            PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
            publishing.getPublications().create("pluginApi", MavenPublication.class, publication -> {
                publication.setGroupId(project.getGroup().toString());
                publication.setArtifactId(project.getName() + "-plugin-api");
                publication.setVersion(extension.getVersion().get());
                publication.artifact(apiJar, artifact -> artifact.setClassifier(null));
                publication.artifact(sources, artifact -> artifact.setClassifier("sources"));
                publication.getPom().withXml(xml -> {
                    Node dependencies = xml.asNode().appendNode("dependencies");
                    shared.getDependencies().forEach(dependency -> {
                        Node item = dependencies.appendNode("dependency");
                        item.appendNode("groupId", dependency.getGroup());
                        item.appendNode("artifactId", dependency.getName());
                        item.appendNode("version", dependency.getVersion());
                        item.appendNode("scope", "compile");
                    });
                });
            });
            publishing.getRepositories().maven(repository -> {
                repository.setName("sca4dj");
                repository.setUrl(project.getRootProject().getLayout().getBuildDirectory().dir("repository"));
            });
        });
        project.getTasks().named("processResources", org.gradle.language.jvm.tasks.ProcessResources.class)
                .configure(task -> {
                    task.dependsOn(index);
                    task.from(project.getLayout().getBuildDirectory().dir("generated/plugin-api-index"));
                });
        project.getTasks().named("classes").configure(task -> task.dependsOn(index));
    }

    /** 从编译器描述读取导出类，禁止把整个 App 输出当作 API。 */
    private static Set<String> exports(Path root) {
        Path index = root.resolve("META-INF/sca4dj/api-classes.list");
        if (!Files.exists(index)) return Set.of();
        try {
            return Set.copyOf(Files.readAllLines(index));
        } catch (IOException failure) {
            throw new GradleException("API 导出清单读取失败", failure);
        }
    }

    /** 包含公开类及其编译生成的嵌套类，不包含同包其他业务实现。 */
    private static boolean matches(String name, Set<String> roots) {
        return roots.stream().anyMatch(root -> name.equals(root) || name.startsWith(root + "$"));
    }
}
