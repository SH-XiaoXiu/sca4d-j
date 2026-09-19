package com.sca4dj.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.util.List;

/** 为一个 Gradle 项目提供不依赖技术栈的构建阶段映射。 */
public final class Sca4djBuildMappingPlugin implements Plugin<Project> {
    static final String LOCAL_COMPILE_TASK = "sca4djLocalCompile";
    static final String LOCAL_TEST_TASK = "sca4djLocalTest";
    static final String LOCAL_INTEGRATION_TEST_TASK = "sca4djLocalIntegrationTest";
    static final String LOCAL_ASSEMBLE_TASK = "sca4djLocalAssemble";
    static final String LOCAL_JAVADOC_TASK = "sca4djLocalJavadoc";
    static final String LOCAL_FORMAT_TASK = "sca4djLocalFormat";
    static final String LOCAL_FORMAT_CHECK_TASK = "sca4djLocalFormatCheck";
    static final String LOCAL_AUDIT_TASK = "sca4djLocalAudit";
    static final String LOCAL_FULL_AUDIT_TASK = "sca4djLocalFullAudit";
    static final String LOCAL_PUBLISH_TASK = "sca4djLocalPublish";

    static final String COMPILE_TASK = "sca4djCompile";
    static final String TEST_TASK = "sca4djTest";
    static final String INTEGRATION_TEST_TASK = "sca4djIntegrationTest";
    static final String ASSEMBLE_TASK = "sca4djAssemble";
    static final String JAVADOC_TASK = "sca4djJavadoc";
    static final String FORMAT_TASK = "sca4djFormat";
    static final String FORMAT_CHECK_TASK = "sca4djFormatCheck";
    static final String AUDIT_TASK = "sca4djAudit";
    static final String FULL_AUDIT_TASK = "sca4djFullAudit";
    static final String PUBLISH_TASK = "sca4djPublish";
    static final String BUILD_TASK = "sca4djBuild";
    static final String QUALITY_REPORT_TASK = "sca4djQualityReport";

    private static final List<String> PHASES = List.of(
            LOCAL_COMPILE_TASK, LOCAL_TEST_TASK, LOCAL_INTEGRATION_TEST_TASK, LOCAL_ASSEMBLE_TASK,
            LOCAL_JAVADOC_TASK, LOCAL_FORMAT_TASK, LOCAL_FORMAT_CHECK_TASK, LOCAL_AUDIT_TASK,
            LOCAL_FULL_AUDIT_TASK, LOCAL_PUBLISH_TASK);

    /** 注册本地阶段、聚合阶段和当前项目质量报告。 */
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class);
        Sca4djBuildMapping mapping = project.getExtensions().create(
                "sca4djBuild", Sca4djBuildMapping.class, project);
        for (String phase : PHASES) {
            internal(project, phase);
        }
        aggregate(project, COMPILE_TASK, LOCAL_COMPILE_TASK);
        aggregate(project, TEST_TASK, LOCAL_TEST_TASK);
        aggregate(project, INTEGRATION_TEST_TASK, LOCAL_INTEGRATION_TEST_TASK);
        aggregate(project, ASSEMBLE_TASK, LOCAL_ASSEMBLE_TASK);
        aggregate(project, JAVADOC_TASK, LOCAL_JAVADOC_TASK);
        aggregate(project, FORMAT_TASK, LOCAL_FORMAT_TASK);
        aggregate(project, FORMAT_CHECK_TASK, LOCAL_FORMAT_CHECK_TASK);
        aggregate(project, AUDIT_TASK, LOCAL_AUDIT_TASK);
        aggregate(project, FULL_AUDIT_TASK, LOCAL_FULL_AUDIT_TASK);
        aggregate(project, PUBLISH_TASK, LOCAL_PUBLISH_TASK);

        TaskProvider<QualityGateTask> report = project.getTasks().register(
                QUALITY_REPORT_TASK, QualityGateTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("生成当前项目及已聚合子项目的统一质量报告");
                    task.getQualityLevel().set(mapping.getEffectiveLevel().map(Enum::name));
                    task.getProjectPath().set(project.getPath());
                    task.getFailOnErrors().convention(false);
                    task.getPrintFindings().convention(false);
                    task.getProjectReports().from(mapping.getProjectReports());
                    task.getCheckstyleReports().from(mapping.getCheckstyleReports());
                    task.getSpotBugsReports().from(mapping.getSpotBugsReports());
                    task.getImportedReports().from(mapping.getImportedReports());
                    task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("reports/sca4dj/quality"));
                });

        TaskProvider<Task> build = project.getTasks().register(BUILD_TASK, task -> {
            task.setGroup("build");
            task.setDescription("按当前项目的有效质量级别执行本地构建");
            task.dependsOn(project.getTasks().named(LOCAL_ASSEMBLE_TASK), report);
        });
        project.afterEvaluate(ignored -> configureLevel(project, mapping, report, build));
        project.getTasks().named(LifecycleBasePlugin.BUILD_TASK_NAME).configure(task -> task.setDependsOn(List.of(build)));
    }

    /** 按当前项目有效级别连接本地任务，不影响子项目自己的级别。 */
    private static void configureLevel(
            Project project,
            Sca4djBuildMapping mapping,
            TaskProvider<QualityGateTask> report,
            TaskProvider<Task> build) {
        QualityLevel requested = QualityLevel.parse(
                project.getProviders().gradleProperty("quality.level").getOrNull());
        QualityLevel minimum = mapping.getMinimumLevel().get();
        QualityLevel effective = mapping.getEffectiveLevel().get();
        project.getLogger().lifecycle("Sca4d-J quality | project={} requested={} minimum={} effective={}",
                project.getPath(), requested, minimum, effective);
        if (effective.includes(QualityLevel.AGILE)) {
            var audit = project.getTasks().named(LOCAL_AUDIT_TASK);
            build.configure(task -> task.dependsOn(
                    project.getTasks().named(LOCAL_TEST_TASK),
                    project.getTasks().named(LOCAL_FORMAT_CHECK_TASK),
                    audit));
            report.configure(task -> task.dependsOn(audit));
        }
        if (effective.includes(QualityLevel.ELEGANT)) {
            var fullAudit = project.getTasks().named(LOCAL_FULL_AUDIT_TASK);
            build.configure(task -> task.dependsOn(
                    project.getTasks().named(LOCAL_INTEGRATION_TEST_TASK),
                    fullAudit));
            report.configure(task -> task.dependsOn(fullAudit));
        }
    }

    /** 注册一个只接受映射依赖的本地阶段。 */
    private static TaskProvider<Task> internal(Project project, String name) {
        return project.getTasks().register(name, task -> {
            task.setGroup("sca4dj internal");
            task.setDescription("Sca4d-J 当前项目内部阶段");
        });
    }

    /** 注册一个默认只执行本地阶段、可由 aggregate 继续连接子级的阶段。 */
    private static TaskProvider<Task> aggregate(Project project, String name, String localTask) {
        return project.getTasks().register(name, task -> {
            task.setGroup("sca4dj internal");
            task.setDescription("Sca4d-J 分层聚合阶段");
            task.dependsOn(project.getTasks().named(localTask));
        });
    }
}
