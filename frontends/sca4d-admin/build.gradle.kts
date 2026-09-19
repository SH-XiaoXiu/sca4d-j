plugins {
    id("com.sca4dj.build.mapping")
}

fun pnpmTask(
    name: String,
    script: String,
) = tasks.register<Exec>(name) {
    group = "frontend"
    description = "运行前端 pnpm $script 任务"
    workingDir(layout.projectDirectory)
    commandLine("pnpm", "run", script)
}

val frontendTypecheck = pnpmTask("frontendTypecheck", "typecheck")
val frontendTest = pnpmTask("frontendTest", "test")
val frontendIntegrationTest = pnpmTask("frontendIntegrationTest", "e2e")
val frontendBuild = pnpmTask("frontendBuild", "build")
val frontendFormat = pnpmTask("frontendFormat", "format")
val frontendFormatCheck = pnpmTask("frontendFormatCheck", "format:check")

val eslintReport = layout.buildDirectory.file("reports/sca4dj/input/eslint-app.json")
val frontendAuditReport = layout.buildDirectory.file("reports/sca4dj/input/eslint.json")

val frontendAppAudit = tasks.register<Exec>("frontendAppAudit") {
    group = "verification"
    description = "检查管理端示例自有源码并生成 ESLint JSON 报告"
    workingDir(layout.projectDirectory)
    inputs.files(
        fileTree("apps/sca4d-admin/src"),
        fileTree("apps/sca4d-admin/e2e"),
        fileTree("internal/lint-configs/eslint-config/src"),
        "apps/sca4d-admin/playwright.config.ts",
        "apps/sca4d-admin/package.json",
        "internal/lint-configs/eslint-config/package.json",
        "eslint.config.mjs",
        "package.json",
        "pnpm-lock.yaml",
        "pnpm-workspace.yaml",
    )
    outputs.file(eslintReport)
    isIgnoreExitValue = true
    doFirst {
        eslintReport.get().asFile.parentFile.mkdirs()
        commandLine(
            "pnpm",
            "exec",
            "eslint",
            "apps/sca4d-admin/src",
            "apps/sca4d-admin/e2e",
            "apps/sca4d-admin/playwright.config.ts",
            "--format",
            "json",
            "--output-file",
            eslintReport.get().asFile.absolutePath,
        )
    }
}

val frontendAudit = tasks.register<Exec>("frontendAudit") {
    group = "verification"
    description = "把 ESLint 结果转换为 Sca4d-J 统一审计报告"
    dependsOn(frontendAppAudit)
    workingDir(layout.projectDirectory)
    inputs.files(
        eslintReport,
        layout.projectDirectory.file("quality/eslint-to-sca4dj-report.mjs"),
    )
    outputs.file(frontendAuditReport)
    doFirst {
        frontendAuditReport.get().asFile.parentFile.mkdirs()
        commandLine(
            "node",
            layout.projectDirectory.file("quality/eslint-to-sca4dj-report.mjs").asFile.absolutePath,
            projectDir.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
            frontendAuditReport.get().asFile.absolutePath,
            eslintReport.get().asFile.absolutePath,
        )
    }
}

sca4djBuild {
    compile(frontendTypecheck)
    test(frontendTest)
    integrationTest(frontendIntegrationTest)
    assemble(frontendBuild)
    format(frontendFormat)
    formatCheck(frontendFormatCheck)
    audit(frontendAudit)
    projectReports.from(frontendAuditReport)
}
