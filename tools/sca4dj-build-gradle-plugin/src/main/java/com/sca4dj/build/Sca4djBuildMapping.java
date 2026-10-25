package com.sca4dj.build;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;

/** 声明当前 Gradle 项目参与的构建阶段、审计报告和最低质量级别。 */
public class Sca4djBuildMapping {
    private final Project project;
    private final Property<QualityLevel> minimumLevel;
    private final Provider<QualityLevel> requestedLevel;
    private final ConfigurableFileCollection projectReports;
    private final ConfigurableFileCollection checkstyleReports;
    private final ConfigurableFileCollection spotBugsReports;
    private final ConfigurableFileCollection importedReports;

    /** 创建只服务当前 Gradle 项目的映射。 */
    @Inject
    public Sca4djBuildMapping(Project project, ObjectFactory objects) {
        this.project = project;
        minimumLevel = objects.property(QualityLevel.class).convention(QualityLevel.SHIT);
        requestedLevel = project.getProviders().gradleProperty("quality.level")
                .map(QualityLevel::parse)
                .orElse(QualityLevel.ELEGANT);
        projectReports = objects.fileCollection();
        checkstyleReports = objects.fileCollection();
        spotBugsReports = objects.fileCollection();
        importedReports = objects.fileCollection();
    }

    /** @return 当前项目允许执行的最低质量级别 */
    public Property<QualityLevel> getMinimumLevel() {
        return minimumLevel;
    }

    /** @return 命令请求级别与项目最低级别中更严格的一项 */
    public Provider<QualityLevel> getEffectiveLevel() {
        return requestedLevel.zip(minimumLevel, QualityLevel::stricter);
    }

    /** 把任务映射为当前项目的编译阶段。 */
    public void compile(Object... tasks) {
        dependsOn(Sca4djBuildMappingPlugin.LOCAL_COMPILE_TASK, tasks);
    }

    /** 把任务映射为当前项目的单元测试阶段。 */
    public void test(Object... tasks) {
        dependsOn(Sca4djBuildMappingPlugin.LOCAL_TEST_TASK, tasks);
    }

    /** 把任务映射为当前项目的集成测试阶段。 */
    public void integrationTest(Object... tasks) {
        dependsOn(Sca4djBuildMappingPlugin.LOCAL_INTEGRATION_TEST_TASK, tasks);
    }

    /** 把任务映射为当前项目的装配阶段。 */
    public void assemble(Object... tasks) {
        dependsOn(Sca4djBuildMappingPlugin.LOCAL_ASSEMBLE_TASK, tasks);
    }

    /** 把任务映射为当前项目的 Javadoc 阶段。 */
    public void javadoc(Object... tasks) {
        dependsOn(Sca4djBuildMappingPlugin.LOCAL_JAVADOC_TASK, tasks);
    }

    /** 把任务映射为当前项目的格式化阶段。 */
    public void format(Object... tasks) {
        dependsOn(Sca4djBuildMappingPlugin.LOCAL_FORMAT_TASK, tasks);
    }

    /** 把任务映射为当前项目的格式检查阶段。 */
    public void formatCheck(Object... tasks) {
        dependsOn(Sca4djBuildMappingPlugin.LOCAL_FORMAT_CHECK_TASK, tasks);
    }

    /** 把任务映射为 AGILE 开始执行的基础审计阶段。 */
    public void audit(Object... tasks) {
        dependsOn(Sca4djBuildMappingPlugin.LOCAL_AUDIT_TASK, tasks);
    }

    /** 把任务映射为 ELEGANT 开始执行的完整审计阶段。 */
    public void fullAudit(Object... tasks) {
        dependsOn(Sca4djBuildMappingPlugin.LOCAL_FULL_AUDIT_TASK, tasks);
    }

    /** 把任务映射为当前项目的发布阶段。 */
    public void publish(Object... tasks) {
        dependsOn(Sca4djBuildMappingPlugin.LOCAL_PUBLISH_TASK, tasks);
    }

    /** @return 当前项目自定义审计任务生成的 JSON 报告 */
    public ConfigurableFileCollection getProjectReports() {
        return projectReports;
    }

    /** @return 当前项目的 Checkstyle XML 报告 */
    public ConfigurableFileCollection getCheckstyleReports() {
        return checkstyleReports;
    }

    /** @return 当前项目的 SpotBugs XML 报告 */
    public ConfigurableFileCollection getSpotBugsReports() {
        return spotBugsReports;
    }

    /** @return 已按子项目自身质量级别处理的 JSON 报告 */
    public ConfigurableFileCollection getImportedReports() {
        return importedReports;
    }

    private void dependsOn(String taskName, Object... tasks) {
        project.getTasks().named(taskName).configure(task -> task.dependsOn(tasks));
    }
}
