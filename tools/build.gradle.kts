plugins {
    id("com.sca4dj.build.aggregate")
}

val appPlugin = gradle.includedBuild("sca4dj-app-gradle-plugin")
val buildPlugin = gradle.includedBuild("sca4dj-build-gradle-plugin")

sca4djBuild {
    minimumLevel.set(com.sca4dj.build.QualityLevel.OBSESSIVE)
    compile(appPlugin.task(":classes"), buildPlugin.task(":classes"))
    test(appPlugin.task(":test"), buildPlugin.task(":test"))
    integrationTest(appPlugin.task(":integrationTest"))
    assemble(appPlugin.task(":assemble"), buildPlugin.task(":assemble"))
    format(appPlugin.task(":spotlessApply"), buildPlugin.task(":spotlessApply"))
    formatCheck(appPlugin.task(":spotlessCheck"), buildPlugin.task(":spotlessCheck"))
    fullAudit(
        appPlugin.task(":sca4djQualityReport"),
        buildPlugin.task(":checkstyleMain"),
        buildPlugin.task(":checkstyleTest"),
        buildPlugin.task(":spotbugsMain"),
        buildPlugin.task(":spotbugsTest"),
        buildPlugin.task(":validatePlugins"),
    )
    importedReports.from(
        layout.projectDirectory.file(
            "sca4dj-app-gradle-plugin/build/reports/sca4dj/quality/quality-report.json",
        ),
    )
    checkstyleReports.from(
        layout.projectDirectory.file("sca4dj-build-gradle-plugin/build/reports/checkstyle/main.xml"),
        layout.projectDirectory.file("sca4dj-build-gradle-plugin/build/reports/checkstyle/test.xml"),
    )
    spotBugsReports.from(
        layout.projectDirectory.file("sca4dj-build-gradle-plugin/build/reports/spotbugs/main.xml"),
        layout.projectDirectory.file("sca4dj-build-gradle-plugin/build/reports/spotbugs/test.xml"),
    )
    publish(appPlugin.task(":publishAllPublicationsToSca4djRepository"))
}
