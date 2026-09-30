package com.sca4dj.build;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.github.spotbugs.snom.SpotBugsExtension;
import com.github.spotbugs.snom.SpotBugsTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * 配置活跃 Java 项目的编译、单元测试、格式化、Javadoc 和静态分析约定。
 *
 * <p>Spotless 插件实现见
 * <a href="https://github.com/diffplug/spotless/tree/main/plugin-gradle">spotless/plugin-gradle</a>，
 * SpotBugs Gradle 插件实现见
 * <a href="https://github.com/spotbugs/spotbugs-gradle-plugin">spotbugs-gradle-plugin</a>。
 */
@SuppressWarnings("unused") // Gradle 通过插件描述符反射加载约定插件。
public final class Sca4djJavaPlugin implements Plugin<Project> {
    /** 为应用了 Java 插件的项目登记仓库统一约定。 */
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("com.sca4dj.build.mapping");
        project.getPluginManager().withPlugin("java", ignored -> configureJava(project));
        project.getPluginManager().withPlugin("java-library", ignored -> configureJava(project));
    }

    /** 配置 Java 插件创建的源码集和校验任务。 */
    @SuppressWarnings("UnstableApiUsage") // Gradle 的 Checkstyle SARIF 与失败策略 API 仍标记为 Incubating。
    private void configureJava(Project project) {
        if (project.getExtensions().getExtraProperties().has("sca4djJavaConfigured")) {
            return;
        }
        project.getExtensions().getExtraProperties().set("sca4djJavaConfigured", true);
        Sca4djBuildMapping mapping = project.getExtensions().getByType(Sca4djBuildMapping.class);

        JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        javaExtension.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(21));
        project.getTasks()
                .withType(JavaCompile.class)
                .configureEach(task -> task.getOptions().getCompilerArgs().add("-parameters"));
        project.getTasks().withType(Test.class).configureEach(Test::useJUnitPlatform);
        project.getTasks().withType(Javadoc.class).configureEach(task -> {
            StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();
            options.addBooleanOption("Xdoclint:all,-missing", true);
            options.addBooleanOption("Werror", true);
        });
        configureIntegrationTest(project);
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        Sca4djJavaExtension javaRules = project.getExtensions()
                .create(
                        "sca4djJava",
                        Sca4djJavaExtension.class);

        project.getPluginManager().apply("com.diffplug.spotless");
        SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
        spotless.java(java -> {
            java.target(sourceSets.stream().map(SourceSet::getAllJava).toList());
            java.importOrder("", "java", "javax");
            java.removeUnusedImports();
        });
        project.afterEvaluate(ignored -> spotless.java(java -> {
            java.addStep(JavaMemberOrderStep.create(javaRules.getMemberOrderGroups().get()));
            java.eclipse().configProperties(eclipseFormatterProperties());
            java.trimTrailingWhitespace();
            java.endWithNewline();
        }));
        spotless.format("sql", format -> {
            format.target(
                    sourceSets.stream()
                            .map(sourceSet -> sourceSet.getResources().matching(pattern -> pattern.include("**/*.sql")))
                            .toList());
            format.trimTrailingWhitespace();
            format.endWithNewline();
        });
        spotless.format("xml", format -> {
            format.target(
                    sourceSets.stream()
                            .map(sourceSet -> sourceSet.getResources().matching(pattern -> pattern.include("**/*.xml")))
                            .toList());
            format.trimTrailingWhitespace();
            format.endWithNewline();
        });
        spotless.kotlinGradle(kotlin -> {
            kotlin.target(project.getBuildFile());
            try {
                kotlin.ktlint();
            } catch (IOException exception) {
                throw new UncheckedIOException("无法初始化 Kotlin Gradle 格式化器", exception);
            }
            kotlin.trimTrailingWhitespace();
            kotlin.endWithNewline();
        });

        project.getPluginManager().apply("checkstyle");
        project.getExtensions().configure(CheckstyleExtension.class, checkstyle -> {
            checkstyle.setToolVersion("10.26.1");
            checkstyle.setConfig(resource(project, "checkstyle.xml"));
            checkstyle.setIgnoreFailures(true);
        });
        project.getTasks().withType(Checkstyle.class).configureEach(task -> {
            task.setIgnoreFailures(true);
            task.getReports().getXml().getRequired().set(true);
            task.getReports().getHtml().getRequired().set(true);
            task.getReports().getSarif().getRequired().set(true);
            mapping.getCheckstyleReports().from(task.getReports().getXml().getOutputLocation());
        });
        mapping.fullAudit(project.getTasks().withType(Checkstyle.class));

        project.getPluginManager().apply("com.github.spotbugs");
        project.getExtensions().configure(SpotBugsExtension.class, spotbugs -> {
            spotbugs.getToolVersion().set("4.10.3");
            spotbugs.getIgnoreFailures().set(true);
            spotbugs.getShowProgress().set(true);
            spotbugs.getExcludeFilter().set(resource(project, "spotbugs-exclude.xml").asFile());
        });
        project.getTasks().withType(SpotBugsTask.class).configureEach(task -> {
            var xml = task.getReports().maybeCreate("xml");
            xml.getRequired().set(true);
            task.getReports().maybeCreate("sarif").getRequired().set(true);
            if ("spotbugsMain".equals(task.getName())) {
                mapping.getSpotBugsReports().from(xml.getOutputLocation());
            }
        });
        mapping.fullAudit(project.getTasks().named("spotbugsMain", SpotBugsTask.class));

        mapping.compile(project.getTasks().named("classes"));
        mapping.test(project.getTasks().named("test"));
        mapping.integrationTest(project.getTasks().named("integrationTest"));
        mapping.assemble(project.getTasks().named("assemble"));
        mapping.javadoc(project.getTasks().named("javadoc", Javadoc.class));
        mapping.format(project.getTasks().named("spotlessApply"));
        mapping.formatCheck(project.getTasks().named("spotlessCheck"));

        pinCommonsLangRuntime(project);
        pinSpotBugsAnnotations(project);
    }

    /** 读取随插件发布的 Java 规范文件，不依赖消费项目的目录结构。 */
    private static TextResource resource(Project project, String name) {
        return project.getResources().getText().fromUri(resourceUrl(name));
    }

    /** 返回插件资源地址；资源缺失表示插件构件不完整。 */
    private static URL resourceUrl(String name) {
        return Objects.requireNonNull(
                Sca4djJavaPlugin.class.getResource("/com/sca4dj/quality/" + name),
                "插件缺少内置 Java 规范文件: " + name);
    }

    /** 把 Eclipse XML 配置转换成 Spotless 接受的 `key=value` 属性。 */
    @SuppressWarnings("HttpUrlsUsage") // XML SAX feature 名称是标准标识符，不会发起 HTTP 请求。
    private static String[] eclipseFormatterProperties() {
        try (InputStream input = resourceUrl("eclipse-java-format.xml").openStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var settings = factory.newDocumentBuilder().parse(input).getElementsByTagName("setting");
            String[] properties = new String[settings.getLength()];
            for (int index = 0; index < settings.getLength(); index++) {
                Element setting = (Element) settings.item(index);
                properties[index] = setting.getAttribute("id") + "=" + setting.getAttribute("value");
            }
            return properties;
        } catch (Exception exception) {
            throw new GradleException("无法读取插件内置 Eclipse Java 格式规则", exception);
        }
    }

    /** 在消费项目完成依赖管理配置后，为全部配置固定 Commons Lang 安全修复版本。 */
    private static void pinCommonsLangRuntime(Project project) {
        project.afterEvaluate(
                ignored -> project.getConfigurations()
                        .configureEach(
                                configuration -> configuration
                                        .getResolutionStrategy()
                                        .eachDependency(details -> {
                                            if ("org.apache.commons".equals(details.getRequested().getGroup())
                                                    && "commons-lang3".equals(details.getRequested().getName())) {
                                                details.useVersion("3.20.0");
                                                details.because("Java 项目必须使用包含递归解析安全修复的 Commons Lang 版本");
                                            }
                                        })));
    }

    /** 在消费项目完成依赖管理配置后，使 SpotBugs 注解与分析器版本一致。 */
    private static void pinSpotBugsAnnotations(Project project) {
        project.afterEvaluate(
                ignored -> project.getConfigurations()
                        .named("spotbugs")
                        .configure(configuration -> configuration.getResolutionStrategy().eachDependency(details -> {
                            if ("com.github.spotbugs".equals(details.getRequested().getGroup())
                                    && "spotbugs-annotations".equals(details.getRequested().getName())) {
                                details.useVersion("4.10.3");
                                details.because("SpotBugs 注解必须与分析器版本一致");
                            }
                        })));
    }

    /** 创建与普通单元测试依赖隔离的 `integrationTest` 源码集和任务。 */
    private static void configureIntegrationTest(Project project) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet test = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
        SourceSet integration = sourceSets.maybeCreate("integrationTest");
        project.getConfigurations()
                .getByName(integration.getImplementationConfigurationName())
                .extendsFrom(project.getConfigurations().getByName(test.getImplementationConfigurationName()));
        project.getConfigurations()
                .getByName(integration.getRuntimeOnlyConfigurationName())
                .extendsFrom(project.getConfigurations().getByName(test.getRuntimeOnlyConfigurationName()));
        integration.setCompileClasspath(
                project.files(
                        main.getOutput(),
                        test.getOutput(),
                        project.getConfigurations().getByName(integration.getCompileClasspathConfigurationName())));
        integration.setRuntimeClasspath(
                project.files(
                        integration.getOutput(),
                        main.getOutput(),
                        test.getOutput(),
                        project.getConfigurations().getByName(integration.getRuntimeClasspathConfigurationName())));
        if (project.getTasks().findByName("integrationTest") == null) {
            project.getTasks().register("integrationTest", Test.class, task -> {
                task.setGroup("verification");
                task.setDescription("运行需要数据库、Redis、Gradle 或生成工程的集成测试");
                task.setTestClassesDirs(integration.getOutput().getClassesDirs());
                task.setClasspath(integration.getRuntimeClasspath());
                task.shouldRunAfter(project.getTasks().named("test"));
            });
        }
    }
}
