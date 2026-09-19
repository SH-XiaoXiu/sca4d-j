package com.sca4dj.build;

/** 保存一条可以定位到源码或配置的审计结果。 */
public final class AuditFinding {
    private final String projectPath;
    private final String qualityLevel;
    private final String ruleId;
    private final String severity;
    private final String path;
    private final int line;
    private final String message;

    /**
     * 创建审计结果。
     *
     * @param ruleId 稳定规则编号
     * @param severity `ERROR` 或 `WARNING`
     * @param path 仓库相对路径
     * @param line 起始行号；无法定位时为 {@code 0}
     * @param message 具体问题和修复方向
     */
    public AuditFinding(String ruleId, String severity, String path, int line, String message) {
        this("", "", ruleId, severity, path, line, message);
    }

    /** 创建包含所属项目和有效质量级别的审计结果。 */
    public AuditFinding(
            String projectPath,
            String qualityLevel,
            String ruleId,
            String severity,
            String path,
            int line,
            String message) {
        this.projectPath = projectPath;
        this.qualityLevel = qualityLevel;
        this.ruleId = ruleId;
        this.severity = severity;
        this.path = path;
        this.line = line;
        this.message = message;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getQualityLevel() {
        return qualityLevel;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getSeverity() {
        return severity;
    }

    public String getPath() {
        return path;
    }

    public int getLine() {
        return line;
    }

    public String getMessage() {
        return message;
    }

    /** @return 内容不变但补充当前项目和有效质量级别的结果 */
    public AuditFinding withContext(String ownerProjectPath, String effectiveQualityLevel) {
        return new AuditFinding(ownerProjectPath, effectiveQualityLevel, ruleId, severity, path, line, message);
    }
}
