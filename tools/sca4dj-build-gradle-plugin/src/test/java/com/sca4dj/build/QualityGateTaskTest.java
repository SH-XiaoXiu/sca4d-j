package com.sca4dj.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** 验证统一质量门对外部分析报告的路径、消息和重复项处理。 */
class QualityGateTaskTest {

    @TempDir
    Path projectDirectory;

    @Test
    @DisplayName("保留 SpotBugs 主位置和详细消息并删除重复结果")
    void preservesSpotBugsLocationAndDeduplicatesFindings() throws Exception {
        Path sourceRoot = projectDirectory.resolve("module/src/main/java");
        Path source = sourceRoot.resolve("example/Sample.java");
        Files.createDirectories(Objects.requireNonNull(source.getParent()));
        Files.writeString(source, "package example; class Sample {}\n");
        Path report = projectDirectory.resolve("module/build/reports/spotbugs/main.xml");
        Files.createDirectories(Objects.requireNonNull(report.getParent()));
        Files.writeString(report, """
                <?xml version="1.0" encoding="UTF-8"?>
                <BugCollection>
                  <Project>
                    <SrcDir>__SOURCE_ROOT__</SrcDir>
                  </Project>
                  <BugInstance type="EXAMPLE" priority="2" category="STYLE">
                    <LongMessage>Detailed SpotBugs finding</LongMessage>
                    <SourceLine sourcepath="example/Sample.java" start="1" end="30"/>
                    <SourceLine primary="true" sourcepath="example/Sample.java" start="12" end="12"/>
                  </BugInstance>
                  <BugInstance type="EXAMPLE" priority="2" category="STYLE">
                    <LongMessage>Detailed SpotBugs finding</LongMessage>
                    <SourceLine primary="true" sourcepath="example/Sample.java" start="12" end="12"/>
                  </BugInstance>
                </BugCollection>
                """.replace("__SOURCE_ROOT__", sourceRoot.toString()));

        Project project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build();
        QualityGateTask task = project.getTasks().create("audit", QualityGateTask.class);
        task.getQualityLevel().set(QualityLevel.ELEGANT.name());
        task.getProjectPath().set(":module");
        task.getFailOnErrors().set(false);
        task.getPrintFindings().set(false);
        task.getSpotBugsReports().from(report.toFile());
        task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("reports/quality"));

        task.evaluate();

        JsonNode findings = new ObjectMapper()
                .readTree(task.getOutputDirectory().file("quality-report.json").get().getAsFile())
                .path("findings");
        assertEquals(1, findings.size());
        assertEquals("module/src/main/java/example/Sample.java", findings.get(0).path("path").asText());
        assertEquals(12, findings.get(0).path("line").asInt());
        assertEquals("Detailed SpotBugs finding", findings.get(0).path("message").asText());
        assertEquals(":module", findings.get(0).path("projectPath").asText());
        assertEquals("ELEGANT", findings.get(0).path("qualityLevel").asText());
    }

    @Test
    @DisplayName("父项目不重新提升子项目已经归一化的警告")
    void preservesImportedFindingSeverity() throws Exception {
        Path childReport = projectDirectory.resolve("child-quality-report.json");
        Files.writeString(childReport, """
                {
                  "qualityLevel": "ELEGANT",
                  "projectPath": ":child",
                  "findings": [{
                    "projectPath": ":child",
                    "qualityLevel": "ELEGANT",
                    "ruleId": "CHILD-WARNING",
                    "severity": "WARNING",
                    "path": "child/src/Example.java",
                    "line": 12,
                    "message": "child warning"
                  }]
                }
                """);

        Project project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build();
        QualityGateTask task = project.getTasks().create("parentAudit", QualityGateTask.class);
        task.getQualityLevel().set(QualityLevel.OBSESSIVE.name());
        task.getProjectPath().set(":");
        task.getFailOnErrors().set(false);
        task.getPrintFindings().set(false);
        task.getImportedReports().from(childReport.toFile());
        task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("reports/parent-quality"));

        task.evaluate();

        JsonNode findings = new ObjectMapper()
                .readTree(task.getOutputDirectory().file("quality-report.json").get().getAsFile())
                .path("findings");
        assertEquals(1, findings.size());
        assertEquals("WARNING", findings.get(0).path("severity").asText());
        assertEquals(":child", findings.get(0).path("projectPath").asText());
        assertEquals("ELEGANT", findings.get(0).path("qualityLevel").asText());
    }
}
