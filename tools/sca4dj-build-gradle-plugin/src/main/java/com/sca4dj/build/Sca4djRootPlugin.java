package com.sca4dj.build;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 提供 Sca4d-J 根项目的公开生命周期任务和最终质量门。
 *
 * <p>子项目只通过 {@code com.sca4dj.build.aggregate} 逐级上传，Root 不递归读取项目树或文件目录。
 */
public final class Sca4djRootPlugin implements Plugin<Project> {

    /** 应用根聚合并把公开任务连接到本级聚合阶段。 */
    @Override
    public void apply(Project project) {
        if (project != project.getRootProject()) {
            throw new IllegalArgumentException("com.sca4dj.build.root 只能应用到根项目");
        }
        project.getPluginManager().apply("com.sca4dj.build.aggregate");
        configureBuildMetadata(project);

        lifecycle(project, "compile", "编译全部已映射项目", Sca4djBuildMappingPlugin.COMPILE_TASK);
        lifecycle(project, "test", "运行全部已映射项目的单元测试", Sca4djBuildMappingPlugin.TEST_TASK);
        lifecycle(
                project,
                "integrationTest",
                "运行全部已映射项目的集成测试",
                Sca4djBuildMappingPlugin.INTEGRATION_TEST_TASK);
        lifecycle(
                project,
                "javadoc",
                "生成全部已映射 Java 项目的 Javadoc",
                Sca4djBuildMappingPlugin.JAVADOC_TASK);
        lifecycle(project, "format", "格式化全部已映射项目", Sca4djBuildMappingPlugin.FORMAT_TASK);
        lifecycle(
                project,
                "formatCheck",
                "检查全部已映射项目的格式",
                Sca4djBuildMappingPlugin.FORMAT_CHECK_TASK);

        TaskProvider<QualityGateTask> report = project.getTasks()
                .named(
                        Sca4djBuildMappingPlugin.QUALITY_REPORT_TASK,
                        QualityGateTask.class);
        report.configure(task -> {
            task.setDescription("汇总全部逐级报告并统一决定仓库构建结果");
            task.getFailOnErrors().set(true);
            task.getPrintFindings().set(true);
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("reports/quality"));
        });
        project.getTasks().register("audit", task -> {
            task.setGroup("verification");
            task.setDescription("按各项目有效质量级别执行审计并输出统一报告");
            task.dependsOn(report);
        });
        project.getTasks().register("publishSca4djArtifacts", task -> {
            task.setGroup("publishing");
            task.setDescription("发布全部已映射项目的公共构件");
            task.dependsOn(project.getTasks().named(Sca4djBuildMappingPlugin.PUBLISH_TASK));
        });

    }

    /** 计算构件标识，并把应用和生成器需要的元数据放入根扩展属性。 */
    private static void configureBuildMetadata(Project project) {
        String buildTime = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        String buildCommit = git(project, "rev-parse", "--short", "HEAD", "dev");
        String buildBranch = git(project, "branch", "--show-current", "detached");
        String buildAuthor = git(project, "log", "-1", "--format=%an", "unknown");
        boolean release = project.getProviders().gradleProperty("release").isPresent();
        Properties defaults = new Properties();
        var defaultsFile = project.getRootDir().toPath().resolve("version.properties");
        if (Files.exists(defaultsFile)) {
            try (var input = Files.newInputStream(defaultsFile)) {
                defaults.load(input);
            } catch (IOException exception) {
                throw new GradleException("无法读取版本声明", exception);
            }
        }
        String productVersion = project.getProviders()
                .gradleProperty("release.version")
                .orElse(project.getProviders().environmentVariable("SCA4DJ_RELEASE_VERSION"))
                .getOrElse(defaults.getProperty("releaseVersion", "0.0.1"));
        if (!productVersion.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")) {
            throw new GradleException("release.version 必须为三段非负整数版本");
        }
        String releaseId = project.getProviders()
                .gradleProperty("release.id")
                .orElse(project.getProviders().environmentVariable("SCA4DJ_RELEASE_ID"))
                .orElse(project.getProviders().environmentVariable("CI_PIPELINE_ID"))
                .getOrElse("local-" + buildCommit + "-" + Instant.now().toEpochMilli());
        if (!releaseId.matches("[A-Za-z0-9._-]{1,120}")) {
            throw new GradleException("release.id 只能包含字母、数字、点、下划线和短横线");
        }
        if (release && releaseId.startsWith("local-")) {
            throw new GradleException("正式构建必须提供 release.id 或 CI_PIPELINE_ID");
        }
        int contract = Integer.parseInt(defaults.getProperty("apiContractVersion", "1"));
        if (contract <= 0)
            throw new GradleException("apiContractVersion 必须为正数");
        String mode = release ? "release" : "dev";
        String version = release ? productVersion + "-" + buildCommit : buildCommit + "-" + buildTime;

        project.getAllprojects().forEach(candidate -> {
            candidate.setGroup("com.sca4dj");
            candidate.setVersion("0.0.1-alpha");
        });
        project.getExtensions().getExtraProperties().set("sca4dBuildTime", buildTime);
        project.getExtensions().getExtraProperties().set("sca4dBuildCommit", buildCommit);
        project.getExtensions().getExtraProperties().set("sca4dBuildBranch", buildBranch);
        project.getExtensions().getExtraProperties().set("sca4dBuildAuthor", buildAuthor);
        project.getExtensions().getExtraProperties().set("sca4dBuildMode", mode);
        project.getExtensions().getExtraProperties().set("sca4dBuildVersion", version);
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("product", defaults.getProperty("product", project.getName()));
        metadata.put("releaseVersion", productVersion);
        metadata.put("releaseId", releaseId);
        metadata.put("sourceRevision", git(project, "rev-parse", "HEAD", "unknown"));
        metadata.put("builtAt", Instant.now().toString());
        metadata.put("buildMode", mode);
        metadata.put("buildVersion", version);
        metadata.put("apiContractVersion", Integer.toString(contract));
        project.getTasks().register("generateBuildManifest", BuildManifestTask.class, task -> {
            task.getMetadata().set(metadata);
            task.getComponent().set("repository");
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("version"));
        });
        project.getAllprojects()
                .forEach(
                        candidate -> candidate.getPluginManager()
                                .withPlugin(
                                        "org.springframework.boot",
                                        ignored -> candidate.getPluginManager().withPlugin("java", javaPlugin -> {
                                            TaskProvider<BuildManifestTask> manifest = candidate == project
                                                    ? project.getTasks()
                                                            .named("generateBuildManifest", BuildManifestTask.class)
                                                    : candidate.getTasks()
                                                            .register("generateBuildManifest", BuildManifestTask.class);
                                            manifest.configure(task -> {
                                                task.getMetadata().set(metadata);
                                                task.getComponent().set(candidate.getName());
                                                if (candidate != project)
                                                    task.getOutputDirectory()
                                                            .set(
                                                                    candidate.getLayout()
                                                                            .getBuildDirectory()
                                                                            .dir("generated/version"));
                                            });
                                            candidate.getExtensions()
                                                    .getByType(JavaPluginExtension.class)
                                                    .getSourceSets()
                                                    .getByName("main")
                                                    .getResources()
                                                    .srcDir(manifest.flatMap(BuildManifestTask::getOutputDirectory));
                                            candidate.getTasks()
                                                    .named("processResources", ProcessResources.class)
                                                    .configure(task -> task.dependsOn(manifest));
                                        })));
        project.getLogger().lifecycle("Building version: {} ({}, release {})", version, mode, releaseId);
    }

    /** 执行三个参数的只读 Git 查询；没有结果时使用默认值。 */
    private static String git(Project project, String first, String second, String third, String fallback) {
        String value = project.getProviders().exec(spec -> {
            spec.commandLine("git", first, second, third);
            spec.setIgnoreExitValue(true);
        }).getStandardOutput().getAsText().get().trim();
        return value.isBlank() ? fallback : value;
    }

    /** 执行两个参数的只读 Git 查询；没有结果时使用默认值。 */
    private static String git(Project project, String first, String second, String fallback) {
        String value = project.getProviders().exec(spec -> {
            spec.commandLine("git", first, second);
            spec.setIgnoreExitValue(true);
        }).getStandardOutput().getAsText().get().trim();
        return value.isBlank() ? fallback : value;
    }

    /** 注册一个只连接本级聚合阶段的仓库公开任务。 */
    private static TaskProvider<Task> lifecycle(Project project, String name, String description, String phase) {
        return project.getTasks().register(name, task -> {
            task.setGroup("build");
            task.setDescription(description);
            task.dependsOn(project.getTasks().named(phase));
        });
    }
}
