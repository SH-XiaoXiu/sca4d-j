package com.sca4d.app.server.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 锁定不同运行环境的日志级别、SQL 输出和归档策略。 */
class LoggingConfigurationTest {

    @Test
    @DisplayName("Keeps Root At Info And Applies Application Profile Levels")
    void keepsRootAtInfoAndAppliesApplicationProfileLevels() throws IOException {
        String logback = resource("logback-spring.xml");

        assertThat(logback)
                .contains("<springProfile name=\"prod\">", "<logger name=\"com.sca4d\" level=\"DEBUG\"/>")
                .contains("<springProfile name=\"dev | local\">", "<logger name=\"com.sca4d\" level=\"TRACE\"/>")
                .contains("<root level=\"INFO\">")
                .doesNotContain("<root level=\"DEBUG\">", "<root level=\"TRACE\">");
    }

    @Test
    @DisplayName("Writes Compressed Size And Time Based ECS JSON Archives")
    void writesCompressedSizeAndTimeBasedEcsJsonArchives() throws IOException {
        String logback = resource("logback-spring.xml");

        assertThat(logback)
                .contains("StructuredLogEncoder")
                .contains("<format>ecs</format>")
                .contains("SizeAndTimeBasedRollingPolicy")
                .contains("application.%d{yyyy-MM-dd}.%i.json.gz")
                .contains("<maxFileSize>100MB</maxFileSize>")
                .contains("<maxHistory>30</maxHistory>")
                .contains("<totalSizeCap>10GB</totalSizeCap>");
        assertThat(resource("application.yml"))
                .contains("path: ${SCA4D_LOG_PATH:${SCA4D_LOG_ROOT:logs}/${spring.application.name}/")
                .contains("exclude: sessionId");
    }

    @Test
    @DisplayName("Enables MyBatis Slf4j SQL Logging Only For Local And Dev")
    void enablesMyBatisSlf4jSqlLoggingOnlyForLocalAndDev() throws IOException {
        assertThat(resource("application-local.yml"))
                .contains("log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl");
        assertThat(resource("application-dev.yml"))
                .contains("log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl");
        assertThat(resource("application.yml"))
                .doesNotContain("org.apache.ibatis.logging.slf4j.Slf4jImpl");
    }

    private static String resource(String name) throws IOException {
        return Files.readString(Path.of("src/main/resources", name));
    }
}
