package com.sca4dj.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** 汇总各分析器报告，完整输出问题后按质量级别决定构建结果。 */
public abstract class QualityGateTask extends DefaultTask {

    /** @return 当前项目最终采用的质量级别名称 */
    @Input
    public abstract Property<String> getQualityLevel();

    /** @return 当前报告所属的 Gradle 项目路径 */
    @Input
    public abstract Property<String> getProjectPath();

    /** @return 报告包含阻断问题时是否让任务失败 */
    @Input
    public abstract Property<Boolean> getFailOnErrors();

    /** @return 是否在控制台逐条输出全部审计结果 */
    @Input
    public abstract Property<Boolean> getPrintFindings();

    /** @return 当前项目自定义审计任务生成的 JSON 报告 */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getProjectReports();

    /** @return 当前项目的 Checkstyle XML 报告 */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getCheckstyleReports();

    /** @return 当前项目的 SpotBugs XML 报告 */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSpotBugsReports();

    /** @return 已经按子项目自身质量级别归一化的统一 JSON 报告 */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getImportedReports();

    /** @return 当前项目统一报告的输出目录 */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /** 读取全部报告，生成 JSON、SARIF、HTML 和控制台摘要。 */
    @TaskAction
    public void evaluate() {
        QualityLevel level = QualityLevel.parse(getQualityLevel().get());
        List<AuditFinding> localFindings = new ArrayList<>();
        files(getProjectReports()).forEach(report -> readProjectReport(report, localFindings));
        files(getCheckstyleReports()).forEach(report -> readCheckstyle(report, localFindings));
        files(getSpotBugsReports()).forEach(report -> readSpotBugs(report, localFindings));

        List<AuditFinding> merged = deduplicate(localFindings).stream()
                .map(finding -> normalize(level, finding))
                .map(finding -> finding.withContext(getProjectPath().get(), level.name()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        files(getImportedReports()).forEach(report -> readImportedReport(report, merged));
        List<AuditFinding> normalized = deduplicate(merged).stream()
                .sorted(Comparator.comparing(AuditFinding::getSeverity)
                        .thenComparing(AuditFinding::getProjectPath)
                        .thenComparing(AuditFinding::getRuleId)
                        .thenComparing(AuditFinding::getPath)
                        .thenComparingInt(AuditFinding::getLine))
                .toList();
        writeReports(level, normalized);
        if (getPrintFindings().get()) {
            printFindings(level, normalized);
        }
        long errors = normalized.stream().filter(finding -> "ERROR".equals(finding.getSeverity())).count();
        if (errors > 0 && getFailOnErrors().get()) {
            throw new GradleException("质量审计未通过: " + errors + " 项阻断问题，完整结果见 build/reports/quality");
        }
    }

    /** OBSESSIVE 把所有非通过结果提升为阻断问题。 */
    private static AuditFinding normalize(QualityLevel level, AuditFinding finding) {
        if (level == QualityLevel.OBSESSIVE && !"ERROR".equals(finding.getSeverity())) {
            return new AuditFinding(finding.getProjectPath(), finding.getQualityLevel(), finding.getRuleId(), "ERROR",
                    finding.getPath(), finding.getLine(), finding.getMessage());
        }
        return finding;
    }

    /** 返回存在的输入报告，并保持稳定顺序。 */
    private static List<File> files(ConfigurableFileCollection reports) {
        return reports.getFiles().stream()
                .filter(File::isFile)
                .sorted(Comparator.comparing(File::getPath))
                .toList();
    }

    /** 读取仓库自定义 JSON 报告。 */
    private static void readProjectReport(File report, List<AuditFinding> findings) {
        try {
            for (JsonNode item : new ObjectMapper().readTree(report)) {
                findings.add(new AuditFinding(
                        item.path("ruleId").asText(),
                        item.path("severity").asText("WARNING"),
                        item.path("path").asText(),
                        item.path("line").asInt(),
                        item.path("message").asText()));
            }
        } catch (IOException exception) {
            throw new GradleException("无法读取审计报告: " + report, exception);
        }
    }

    /** 读取直接子项目已经按自身最低级别归一化的统一报告。 */
    private static void readImportedReport(File report, List<AuditFinding> findings) {
        try {
            for (JsonNode item : new ObjectMapper().readTree(report).path("findings")) {
                findings.add(new AuditFinding(
                        item.path("projectPath").asText(),
                        item.path("qualityLevel").asText(),
                        item.path("ruleId").asText(),
                        item.path("severity").asText("WARNING"),
                        item.path("path").asText(),
                        item.path("line").asInt(),
                        item.path("message").asText()));
            }
        } catch (IOException exception) {
            throw new GradleException("无法读取子项目质量报告: " + report, exception);
        }
    }

    /** 读取 Checkstyle XML 报告。 */
    private void readCheckstyle(File report, List<AuditFinding> findings) {
        Element root = parseXml(report);
        var files = root.getElementsByTagName("file");
        for (int fileIndex = 0; fileIndex < files.getLength(); fileIndex++) {
            Element file = (Element) files.item(fileIndex);
            var errors = file.getElementsByTagName("error");
            for (int errorIndex = 0; errorIndex < errors.getLength(); errorIndex++) {
                Element error = (Element) errors.item(errorIndex);
                String source = error.getAttribute("source");
                String rule = source.substring(source.lastIndexOf('.') + 1);
                findings.add(new AuditFinding("DOC-" + rule, "WARNING", repositoryRelative(file.getAttribute("name")),
                        integer(error.getAttribute("line")), error.getAttribute("message")));
            }
        }
    }

    /** 读取 SpotBugs XML，高优先级正确性、安全和并发问题直接阻断 ELEGANT。 */
    private void readSpotBugs(File report, List<AuditFinding> findings) {
        Element root = parseXml(report);
        var bugs = root.getElementsByTagName("BugInstance");
        for (int index = 0; index < bugs.getLength(); index++) {
            Element bug = (Element) bugs.item(index);
            String category = bug.getAttribute("category");
            boolean serious = "1".equals(bug.getAttribute("priority"))
                    && ("CORRECTNESS".equals(category) || "MT_CORRECTNESS".equals(category)
                            || "SECURITY".equals(category) || "MALICIOUS_CODE".equals(category));
            Element source = primarySourceLine(bug);
            String path = source == null
                    ? firstClassName(bug)
                    : spotBugsSourcePath(root, source.getAttribute("sourcepath"));
            int line = source == null ? 0 : integer(source.getAttribute("start"));
            Element longMessage = firstElement(bug, "LongMessage");
            String message = longMessage == null || longMessage.getTextContent().isBlank()
                    ? bug.getAttribute("type") + " | category=" + category
                    : longMessage.getTextContent().trim();
            findings.add(new AuditFinding("SPOTBUGS-" + bug.getAttribute("type"),
                    serious ? "ERROR" : "WARNING", path, line, message));
        }
    }

    /** 使用禁止外部实体的 XML 解析器读取工具报告。 */
    private static Element parseXml(File report) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(report).getDocumentElement();
        } catch (Exception exception) {
            throw new GradleException("无法解析工具报告: " + report, exception);
        }
    }

    /** 返回节点下第一个指定元素。 */
    private static Element firstElement(Element parent, String name) {
        var elements = parent.getElementsByTagName(name);
        return elements.getLength() == 0 ? null : (Element) elements.item(0);
    }

    /** 返回标记为主要位置的元素；旧报告没有标记时使用第一个同名元素。 */
    private static Element primarySourceLine(Element parent) {
        var elements = parent.getElementsByTagName("SourceLine");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if ("true".equals(element.getAttribute("primary"))) {
                return element;
            }
        }
        return elements.getLength() == 0 ? null : (Element) elements.item(0);
    }

    /** 返回 SpotBugs 类名作为无法定位源码时的对象标识。 */
    private static String firstClassName(Element bug) {
        Element type = firstElement(bug, "Class");
        return type == null ? "" : type.getAttribute("classname");
    }

    /** 使用 SpotBugs 报告声明的源码目录恢复仓库相对文件位置。 */
    private String spotBugsSourcePath(Element report, String sourcePath) {
        var sourceDirectories = report.getElementsByTagName("SrcDir");
        for (int index = 0; index < sourceDirectories.getLength(); index++) {
            Path candidate = Path.of(sourceDirectories.item(index).getTextContent()).resolve(sourcePath).normalize();
            if (Files.isRegularFile(candidate)) {
                return repositoryRelative(candidate.toString());
            }
        }
        return sourcePath;
    }

    /** 把仓库内绝对路径转换为与本机目录名无关的相对路径。 */
    private String repositoryRelative(String value) {
        Path supplied = Path.of(value);
        if (!supplied.isAbsolute()) {
            return value;
        }
        Path path = supplied.normalize();
        Path root = getProject().getRootProject().getProjectDir().toPath().toAbsolutePath().normalize();
        return path.startsWith(root) ? root.relativize(path).toString() : value;
    }

    /** 解析可能为空的整数属性。 */
    private static int integer(String value) {
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
    }

    /** 删除多个分析器或重复输入产生的完全相同结果。 */
    private static List<AuditFinding> deduplicate(List<AuditFinding> findings) {
        Map<String, AuditFinding> unique = new LinkedHashMap<>();
        for (AuditFinding finding : findings) {
            String key = String.join("\u0000",
                    finding.getProjectPath(),
                    finding.getQualityLevel(),
                    finding.getRuleId(),
                    finding.getSeverity(),
                    finding.getPath(),
                    Integer.toString(finding.getLine()),
                    finding.getMessage());
            unique.putIfAbsent(key, finding);
        }
        return List.copyOf(unique.values());
    }

    /** 写出统一 JSON、SARIF 和 HTML。 */
    private void writeReports(QualityLevel level, List<AuditFinding> findings) {
        File directory = getOutputDirectory().get().getAsFile();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new GradleException("无法创建统一质量报告目录: " + directory);
        }
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("qualityLevel", level.name());
        summary.put("projectPath", getProjectPath().get());
        summary.put("findings", findings);
        try {
            mapper.writeValue(new File(directory, "quality-report.json"), summary);
            mapper.writeValue(new File(directory, "quality-report.sarif.json"), sarif(findings));
            Files.writeString(new File(directory, "quality-report.html").toPath(), html(level, findings),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new GradleException("无法写入统一质量报告", exception);
        }
    }

    /** 创建最小 SARIF 2.1.0 报告结构。 */
    private static Map<String, Object> sarif(List<AuditFinding> findings) {
        List<Map<String, Object>> results = findings.stream().map(finding -> Map.of(
                "ruleId", finding.getRuleId(),
                "level", "ERROR".equals(finding.getSeverity()) ? "error" : "warning",
                "message", Map.of("text", finding.getMessage()),
                "locations", List.of(Map.of("physicalLocation", Map.of(
                        "artifactLocation", Map.of("uri", finding.getPath()),
                        "region", Map.of("startLine", Math.max(1, finding.getLine())))))))
                .toList();
        return Map.of(
                "version", "2.1.0",
                "$schema", "https://json.schemastore.org/sarif-2.1.0.json",
                "runs", List.of(Map.of("tool", Map.of("driver", Map.of("name", "Sca4d-J Quality")),
                        "results", results)));
    }

    /** 创建适合本地浏览的静态 HTML。 */
    private static String html(QualityLevel level, List<AuditFinding> findings) {
        StringBuilder rows = new StringBuilder();
        for (AuditFinding finding : findings) {
            rows.append("<tr><td>").append(escape(finding.getSeverity())).append("</td><td>")
                    .append(escape(finding.getRuleId())).append("</td><td>")
                    .append(escape(finding.getPath())).append(':').append(finding.getLine()).append("</td><td>")
                    .append(escape(finding.getMessage())).append("</td></tr>");
        }
        return "<!doctype html><meta charset=\"utf-8\"><title>Sca4d-J Quality</title>"
                + "<h1>Sca4d-J " + level.name() + " 审计</h1><table border=\"1\">"
                + "<thead><tr><th>级别</th><th>规则</th><th>位置</th><th>问题</th></tr></thead><tbody>"
                + rows + "</tbody></table>";
    }

    /** 转义 HTML 文本。 */
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** 在最终失败前完整输出本次所有结果。 */
    private void printFindings(QualityLevel level, List<AuditFinding> findings) {
        getLogger().lifecycle("Sca4d-J 质量审计 | project={} level={} findings={}",
                getProjectPath().get(), level, findings.size());
        for (AuditFinding finding : findings) {
            getLogger().lifecycle("{} | project={} effective={} rule={} path={}:{} message={}",
                    finding.getSeverity(), finding.getProjectPath(), finding.getQualityLevel(), finding.getRuleId(),
                    finding.getPath(), finding.getLine(), finding.getMessage());
        }
    }
}
