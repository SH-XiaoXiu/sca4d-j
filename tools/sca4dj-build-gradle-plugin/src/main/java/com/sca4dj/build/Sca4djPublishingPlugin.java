package com.sca4dj.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

/** 配置 Core、Module 和 Starter 使用相同的 Maven 发布目标。 */
public final class Sca4djPublishingPlugin implements Plugin<Project> {

    /** 为当前 Java 构件创建 `mavenJava` 发布。 */
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("com.sca4dj.build.mapping");
        project.getPluginManager().apply("maven-publish");
        project.getPluginManager().withPlugin("java", ignored -> configurePublishing(project));
        project.getPluginManager().withPlugin("java-library", ignored -> configurePublishing(project));
    }

    /** 配置发布内容和仓库位置。 */
    private void configurePublishing(Project project) {
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        if (publishing.getPublications().findByName("mavenJava") == null) {
            publishing.getPublications().create("mavenJava", MavenPublication.class,
                    publication -> publication.from(project.getComponents().getByName("java")));
        }
        publishing.getRepositories().maven(repository -> {
            repository.setName("sca4dj");
            repository.setUrl(project.getProviders().gradleProperty("sca4dj.repository")
                    .orElse(project.getRootProject().getLayout().getBuildDirectory().dir("repository")
                            .map(directory -> directory.getAsFile().toURI().toString())));
        });
        Sca4djBuildMapping mapping = project.getExtensions().getByType(Sca4djBuildMapping.class);
        project.afterEvaluate(ignored -> {
            if (project.getTasks().findByName("publishAllPublicationsToSca4djRepository") != null) {
                mapping.publish(project.getTasks().named("publishAllPublicationsToSca4djRepository"));
            }
        });
    }
}
