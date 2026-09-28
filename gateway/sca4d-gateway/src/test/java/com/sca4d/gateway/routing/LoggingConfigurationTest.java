package com.sca4d.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 锁定 Gateway 控制台输出和 ECS JSON 文件归档配置。 */
class LoggingConfigurationTest {

    @Test
    @DisplayName("保留 Spring Boot 控制台并写入 ECS JSON 滚动文件")
    void keepsConsoleAndWritesRollingEcsJsonFile() throws IOException {
        String logback = resource("logback-spring.xml");

        assertThat(logback)
                .contains("console-appender.xml")
                .contains("StructuredLogEncoder")
                .contains("<format>ecs</format>")
                .contains("application.%d{yyyy-MM-dd}.%i.json.gz")
                .contains("source=\"logging.logback.rollingpolicy.max-file-size\"")
                .contains("source=\"logging.logback.rollingpolicy.max-history\"")
                .contains("source=\"logging.logback.rollingpolicy.total-size-cap\"")
                .contains("<appender-ref ref=\"CONSOLE\"/>")
                .contains("<appender-ref ref=\"ECS_JSON_FILE\"/>");
        assertThat(resource("application.yml"))
                .contains("path: ${SCA4D_LOG_PATH:${SCA4D_LOG_ROOT:logs}/${spring.application.name}/")
                .contains("exclude: sessionId");
        assertThat(resource("application-local.yml"))
                .contains("optional:file:.env/application.env[.properties]")
                .contains("optional:file:.env/logging.env[.properties]")
                .contains("optional:file:../../.env/application.env[.properties]")
                .contains("optional:file:../../.env/logging.env[.properties]");
    }

    /** @return 指定 Gateway classpath 资源的完整文本 */
    private static String resource(String name) throws IOException {
        return Files.readString(Path.of("src/main/resources", name));
    }
}
