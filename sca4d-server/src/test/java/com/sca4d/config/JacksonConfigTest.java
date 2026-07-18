package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.OffsetDateTime;

/** 验证枚举、时间、Long 和 View 的共用 JSON 序列化规则。 */
class JacksonConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(JacksonConfig.class, BaseViewJacksonConfig.class);

    @Test
    void shouldSerializeLongAsStringAndDateAsDefaultZoneIsoText() {
        contextRunner.run(context -> {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

            String json = objectMapper.writeValueAsString(
                    new JsonPayload(9_007_199_254_740_993L, OffsetDateTime.parse("2026-07-09T00:00:00Z")));
            JsonPayload payload = objectMapper.readValue(
                    "{\"id\":\"9007199254740993\",\"createdAt\":\"2026-07-09T00:00:00Z\"}",
                    JsonPayload.class);

            assertThat(json).contains("\"id\":\"9007199254740993\"");
            assertThat(json).contains("\"createdAt\":\"2026-07-09T08:00:00+08:00\"");
            assertThat(payload.getId()).isEqualTo(9_007_199_254_740_993L);
        });
    }

    /** 包含 Long、时间和枚举字段的 JSON 配置测试对象。 */
    private static class JsonPayload {

        private Long id;
        private OffsetDateTime createdAt;

        JsonPayload() {
        }

        JsonPayload(Long id, OffsetDateTime createdAt) {
            this.id = id;
            this.createdAt = createdAt;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public OffsetDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(OffsetDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}
