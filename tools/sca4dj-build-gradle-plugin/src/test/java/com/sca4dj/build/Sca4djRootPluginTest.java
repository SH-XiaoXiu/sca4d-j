package com.sca4dj.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** 验证逐级聚合、最低质量级别和最终失败阈值。 */
class Sca4djRootPluginTest {

    @TempDir
    Path projectDirectory;

    @Test
    @DisplayName("未指定请求级别时使用 ELEGANT")
    void usesElegantByDefault() throws IOException {
        prepareProject(true);

        BuildResult result = runner("help").build();

        assertTrue(result.getOutput().contains("project=: requested=ELEGANT minimum=SHIT effective=ELEGANT"));
    }

    @Test
    @DisplayName("非法请求级别在配置阶段失败")
    void rejectsUnknownQualityLevel() throws IOException {
        prepareProject(true);

        BuildResult result = runner("help", "-Pquality.level=FAST").buildAndFail();

        assertTrue(result.getOutput().contains("quality.level 只能是 SHIT、AGILE、ELEGANT 或 OBSESSIVE"));
    }

    @Test
    @DisplayName("中间项目声明聚合后才上传孙项目")
    void aggregatesGrandchildOnlyThroughConfiguredParent() throws IOException {
        prepareProject(true);

        BuildResult result = runner(":compile", "--dry-run", "-Pquality.level=SHIT").build();

        assertTrue(hasTask(result, ":group:leaf:leafCompile"));
    }

    @Test
    @DisplayName("Javadoc 通过已配置的项目逐级聚合")
    void aggregatesJavadocThroughConfiguredProjects() throws IOException {
        prepareProject(true);

        BuildResult result = runner(":javadoc", "--dry-run", "-Pquality.level=SHIT").build();

        assertTrue(hasTask(result, ":group:leaf:leafJavadoc"));
    }

    @Test
    @DisplayName("未配置聚合的直接子项目被忽略")
    void ignoresUnconfiguredChildProject() throws IOException {
        prepareProject(false);

        BuildResult result = runner(":compile", "--dry-run", "-Pquality.level=SHIT").build();

        assertFalse(hasTask(result, ":group:leaf:leafCompile"));
    }

    @Test
    @DisplayName("模块最低级别自动提升全局请求级别")
    void moduleMinimumRaisesRequestedLevel() throws IOException {
        prepareProject(true);

        BuildResult result = runner(":build", "--dry-run", "-Pquality.level=SHIT").build();

        assertTrue(result.getOutput().contains(
                "project=:group:leaf requested=SHIT minimum=ELEGANT effective=ELEGANT"));
        assertTrue(hasTask(result, ":group:leaf:leafTest"));
        assertTrue(hasTask(result, ":group:leaf:leafIntegrationTest"));
    }

    @Test
    @DisplayName("子项目显式最低级别不被父项目最低级别覆盖")
    void childMinimumOverridesInheritedMinimum() throws IOException {
        prepareProject(true);
        write("group/build.gradle.kts", """
                plugins {
                    id("com.sca4dj.build.aggregate")
                }

                sca4djBuild {
                    minimumLevel.set(com.sca4dj.build.QualityLevel.OBSESSIVE)
                }
                """);

        BuildResult result = runner("help", "-Pquality.level=SHIT").build();

        assertTrue(result.getOutput().contains(
                "project=:group requested=SHIT minimum=OBSESSIVE effective=OBSESSIVE"));
        assertTrue(result.getOutput().contains(
                "project=:group:leaf requested=SHIT minimum=ELEGANT effective=ELEGANT"));
    }

    @Test
    @DisplayName("审计任务不执行单元测试和集成测试")
    void auditDoesNotRunTests() throws IOException {
        prepareProject(true);

        BuildResult result = runner(":audit", "--dry-run", "-Pquality.level=ELEGANT").build();

        assertFalse(hasTask(result, ":group:leaf:leafTest"));
        assertFalse(hasTask(result, ":group:leaf:leafIntegrationTest"));
        assertTrue(hasTask(result, ":group:leaf:leafAudit"));
        assertTrue(hasTask(result, ":group:leaf:leafFullAudit"));
    }

    @Test
    @DisplayName("根插件不再注册 Qodana 任务")
    void doesNotRegisterQodanaTask() throws IOException {
        prepareProject(true);

        BuildResult result = runner("tasks", "--all").build();

        assertFalse(result.getOutput().contains("auditJetBrains"));
    }

    @Test
    @DisplayName("OBSESSIVE 汇总全部警告后失败")
    void obsessiveReportsEveryFindingBeforeFailing() throws IOException {
        prepareProject(true);
        writeProjectWarnings();

        BuildResult result = runner(":audit", "-Pquality.level=OBSESSIVE").buildAndFail();

        assertTrue(result.getOutput().contains("ERROR | project=: effective=OBSESSIVE rule=FIRST-WARNING"));
        assertTrue(result.getOutput().contains("ERROR | project=: effective=OBSESSIVE rule=SECOND-WARNING"));
        assertTrue(result.getOutput().contains("质量审计未通过: 2 项阻断问题"));
    }

    /** 创建 Root、可选中间聚合项目、叶子映射项目和未映射项目。 */
    private void prepareProject(boolean aggregateGroup) throws IOException {
        write("settings.gradle.kts", """
                rootProject.name = "quality-fixture"
                include("group:leaf", "ignored")
                """);
        write("build.gradle.kts", """
                plugins {
                    id("com.sca4dj.build.root")
                }

                sca4djBuild {
                    minimumLevel.set(com.sca4dj.build.QualityLevel.SHIT)
                    projectReports.from(layout.projectDirectory.file("quality-warnings.json"))
                }
                """);
        write("group/build.gradle.kts", aggregateGroup ? """
                plugins {
                    id("com.sca4dj.build.aggregate")
                }
                """ : "");
        write("group/leaf/build.gradle.kts", """
                plugins {
                    id("com.sca4dj.build.mapping")
                }

                val leafCompile = tasks.register("leafCompile")
                val leafTest = tasks.register("leafTest")
                val leafIntegrationTest = tasks.register("leafIntegrationTest")
                val leafAssemble = tasks.register("leafAssemble")
                val leafJavadoc = tasks.register("leafJavadoc")
                val leafFormatCheck = tasks.register("leafFormatCheck")
                val leafAudit = tasks.register("leafAudit")
                val leafFullAudit = tasks.register("leafFullAudit")

                sca4djBuild {
                    minimumLevel.set(com.sca4dj.build.QualityLevel.ELEGANT)
                    compile(leafCompile)
                    test(leafTest)
                    integrationTest(leafIntegrationTest)
                    assemble(leafAssemble)
                    javadoc(leafJavadoc)
                    formatCheck(leafFormatCheck)
                    audit(leafAudit)
                    fullAudit(leafFullAudit)
                }
                """);
        write("ignored/build.gradle.kts", "");
    }

    /** 写入包含两条不同警告的通用项目报告。 */
    private void writeProjectWarnings() throws IOException {
        write("quality-warnings.json", """
                [
                  {"ruleId": "FIRST-WARNING", "severity": "WARNING", "path": "src/First.java", "line": 1,
                   "message": "first finding"},
                  {"ruleId": "SECOND-WARNING", "severity": "WARNING", "path": "src/Second.java", "line": 2,
                   "message": "second finding"}
                ]
                """);
    }

    /** 创建使用当前插件 classpath 的 TestKit 执行器。 */
    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput();
    }

    /** 判断 dry-run 输出是否包含完整任务路径。 */
    private static boolean hasTask(BuildResult result, String taskPath) {
        return result.getOutput().lines().anyMatch(line -> line.startsWith(taskPath + " "));
    }

    /** 写入测试工程文件，并按需创建父目录。 */
    private void write(String relativePath, String content) throws IOException {
        Path target = projectDirectory.resolve(relativePath);
        Files.createDirectories(Objects.requireNonNull(target.getParent()));
        Files.writeString(target, content);
    }
}
