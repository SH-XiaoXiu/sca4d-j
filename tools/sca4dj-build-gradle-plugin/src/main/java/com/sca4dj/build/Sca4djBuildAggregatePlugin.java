package com.sca4dj.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.List;

/** 只把当前 Gradle 项目的直接子项目连接到本级构建映射。 */
public final class Sca4djBuildAggregatePlugin implements Plugin<Project> {
    private static final List<String> AGGREGATED_TASKS = List.of(
            Sca4djBuildMappingPlugin.COMPILE_TASK,
            Sca4djBuildMappingPlugin.TEST_TASK,
            Sca4djBuildMappingPlugin.INTEGRATION_TEST_TASK,
            Sca4djBuildMappingPlugin.ASSEMBLE_TASK,
            Sca4djBuildMappingPlugin.JAVADOC_TASK,
            Sca4djBuildMappingPlugin.FORMAT_TASK,
            Sca4djBuildMappingPlugin.FORMAT_CHECK_TASK,
            Sca4djBuildMappingPlugin.AUDIT_TASK,
            Sca4djBuildMappingPlugin.FULL_AUDIT_TASK,
            Sca4djBuildMappingPlugin.PUBLISH_TASK);

    /** 应用通用映射，并等待直接子项目显式接入同一映射。 */
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("com.sca4dj.build.mapping");
        Sca4djBuildMapping parent = project.getExtensions().getByType(Sca4djBuildMapping.class);
        project.getChildProjects().values().forEach(child -> child.getPluginManager().withPlugin(
                "com.sca4dj.build.mapping", ignored -> connect(project, parent, child)));
    }

    /** 连接一个直接子项目；孙级只能通过该子项目自己的 aggregate 继续上传。 */
    private static void connect(Project parentProject, Sca4djBuildMapping parent, Project child) {
        Sca4djBuildMapping childMapping = child.getExtensions().getByType(Sca4djBuildMapping.class);
        childMapping.getMinimumLevel().convention(parent.getMinimumLevel());
        for (String taskName : AGGREGATED_TASKS) {
            parentProject.getTasks().named(taskName).configure(task -> task.dependsOn(child.getTasks().named(taskName)));
        }
        parentProject.getTasks().named(Sca4djBuildMappingPlugin.BUILD_TASK)
                .configure(task -> task.dependsOn(child.getTasks().named(Sca4djBuildMappingPlugin.BUILD_TASK)));
        var childReport = child.getTasks().named(
                Sca4djBuildMappingPlugin.QUALITY_REPORT_TASK, QualityGateTask.class);
        parentProject.getTasks().named(Sca4djBuildMappingPlugin.QUALITY_REPORT_TASK).configure(task -> task.dependsOn(childReport));
        parent.getImportedReports().from(childReport.flatMap(report -> report.getOutputDirectory().file("quality-report.json")));
    }
}
