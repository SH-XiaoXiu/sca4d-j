package com.sca4dj.gradle.app;

import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/** 把 API 配置作为可追踪编译输入，IDE 与 Gradle 使用相同的注解处理参数。 */
public final class PluginApiCompilerArguments implements CommandLineArgumentProvider {
    private final PluginApiExtension extension;

    private final FileCollection shared;

    public PluginApiCompilerArguments(PluginApiExtension extension, FileCollection shared) {
        this.extension = extension;
        this.shared = shared;
    }

    /**
     * App 明确共享的构件，用于构建时验证外部契约类型。
     *
     * @return App 共享构件的类路径
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getSharedClasspath() {
        return shared;
    }

    /** @return App API 编码配置 */
    @Input
    public Property<String> getApiId() {
        return extension.getId();
    }

    /** @return API 版本配置 */
    @Input
    public Property<String> getApiVersion() {
        return extension.getVersion();
    }

    /** @return 生成代码的包名配置 */
    @Input
    public Property<String> getGeneratedPackage() {
        return extension.getGeneratedPackage();
    }

    /** @return App 插件基类名称配置 */
    @Input
    public Property<String> getEntryName() {
        return extension.getEntryName();
    }

    /** @return App 公开配置类型 */
    @Input
    public Property<String> getConfigurationType() {
        return extension.getConfigurationType();
    }

    @Override
    public Iterable<String> asArguments() {
        return List.of(
                "-Asca4dj.plugin.api.id=" + getApiId().get(),
                "-Asca4dj.plugin.api.version=" + getApiVersion().get(),
                "-Asca4dj.plugin.api.package=" + getGeneratedPackage().get(),
                "-Asca4dj.plugin.api.entry=" + getEntryName().get(),
                "-Asca4dj.plugin.api.configuration=" + getConfigurationType().get(),
                "-Asca4dj.plugin.api.shared=" + shared.getFiles()
                        .stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining(File.pathSeparator)));
    }
}
