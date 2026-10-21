package com.sca4d.core.spring.version;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.version.ApplicationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 验证版本模块只消费配置，不要求任何构建清单或构建工具。 */
class ApplicationVersionConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ApplicationVersionAutoConfiguration.class);

    @Test
    void remainsOptionalWithoutApplicationDeclaration() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ApplicationVersion.class));
    }

    @Test
    void resolvesApplicationVariablesAndValidatesVersionInRuntimeModule() {
        var configured = runner.withPropertyValues(
                "sca4dj.version.enabled=true",
                "sca4dj.version.product=example",
                "sca4dj.version.component=api",
                "sca4dj.version.release-version=1.2.3",
                "sca4dj.version.api-contract-version=2",
                "sca4dj.version.release-id=${RELEASE:local}",
                "sca4dj.version.component-build-id=${BUILD:test}",
                "sca4dj.version.source-revision=unknown",
                "sca4dj.version.built-at=unknown",
                "sca4dj.version.build-mode=dev",
                "sca4dj.version.build-version=${BUILD:test}",
                "BUILD=ci-42");
        configured.run(context -> {
            assertThat(context).hasNotFailed();
            var version = context.getBean(ApplicationVersion.class);
            assertThat(version.componentBuildId()).isEqualTo("ci-42");
            assertThat(version.releaseVersion()).isEqualTo("1.2.3");
            assertThat(version.apiContractVersion()).isEqualTo(2);
        });
        configured.withPropertyValues("sca4dj.version.release-version=dev-abc123")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ApplicationVersion.class).releaseVersion()).isEqualTo("dev-abc123");
                });
        configured.withPropertyValues("sca4dj.version.release-version=bad version")
                .run(context -> assertThat(context).hasFailed());
    }
}
