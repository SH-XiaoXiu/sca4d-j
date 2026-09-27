package com.sca4dj.gradle.app;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.util.Locale;

/** 为 Sca4d-J App 注册应用级数据库初始化任务。 */
@SuppressWarnings("unused") // Gradle 通过插件描述符反射加载插件和无参数构造器。
public final class Sca4djAppPlugin implements Plugin<Project> {

    /** 创建无状态的 App 构建插件。 */
    public Sca4djAppPlugin() {
    }

    /**
     * 注册 `sca4djApp` 配置和 `initFlyway` 任务。
     *
     * @param project 使用插件的 App 项目
     */
    @Override
    public void apply(Project project) {
        Sca4djAppExtension extension = project.getExtensions().create("sca4djApp", Sca4djAppExtension.class);
        extension.getMigrationName().convention(normalizeName(project.getName()));

        project.getPluginManager().withPlugin("java", ignored -> {
            Configuration runtimeClasspath = project.getConfigurations().getByName("runtimeClasspath");
            var initFlyway = project.getTasks().register("initFlyway", InitFlywayTask.class, task -> {
                task.setGroup("application");
                task.setDescription("根据 App 运行模块生成仅一次的 Flyway V1 初始结构");
                task.getRuntimeConfiguration().set(runtimeClasspath);
                task.getRuntimeClasspath().from(runtimeClasspath);
                task.getMigrationName().set(extension.getMigrationName());
                task.getSchemaFragment().set(extension.getSchemaFragment());
                task.getMigrationDirectory().set(project.getLayout().getProjectDirectory().dir(
                        "src/main/resources/db/migration"));
            });
            if (project.getProviders().gradleProperty("sca4dj.init").isPresent()) {
                project.getTasks().named("processResources", ProcessResources.class).configure(task -> task.dependsOn(initFlyway));
            }
        });
    }

    /**
     * 把 Gradle 项目名转换成可以用于迁移文件的稳定名称。
     *
     * @param projectName Gradle 项目名
     * @return 小写下划线名称
     */
    private static String normalizeName(String projectName) {
        return projectName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
