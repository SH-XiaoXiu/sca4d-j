package com.sca4d.app.server.preference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.codec.SettingCodec;
import com.sca4d.core.api.setting.definition.PreferenceSetting;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 注册 Sca4d Admin 可以按 Principal 同步的界面偏好。 */
@Configuration(proxyBeanMethods = false)
public class AdminPreferenceConfiguration {

    /** Vben 管理端完整偏好 JSON。 */
    public final @NotNull PreferenceSetting<JsonNode> UI_PREFERENCES;

    /** 创建管理端偏好设置配置。 */
    public AdminPreferenceConfiguration(@NotNull SettingReader<String> reader, SettingWriter<String> writer) {
        this.UI_PREFERENCES = new PreferenceSetting<>(
                reader,
                writer,
                TreeCode.of("ADMIN:UI_PREFERENCES"),
                JsonNodeFactory.instance.objectNode(),
                SettingCodec.jsonObject(65_536),
                Pipeline.identity(JsonNode.class));
    }

    /** @return Vben 管理端偏好设置定义 */
    @Bean
    PreferenceSetting<JsonNode> adminUiPreferenceSetting() {
        return UI_PREFERENCES;
    }
}
