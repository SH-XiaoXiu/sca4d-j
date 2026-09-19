package com.sca4d.app.server.preference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.PreferenceSetting;
import com.sca4d.core.api.setting.SettingCodec;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 注册 Sca4d Admin 可以按 Principal 同步的界面偏好。 */
@Configuration(proxyBeanMethods = false)
public class AdminPreferenceConfiguration {

    /** Vben 管理端完整偏好 JSON。 */
    public static final @NotNull PreferenceSetting<JsonNode> UI_PREFERENCES = new PreferenceSetting<>(
            "admin.ui-preferences",
            1,
            JsonNodeFactory.instance.objectNode(),
            SettingCodec.jsonObject(65_536),
            Pipeline.identity(JsonNode.class));

    /** 创建管理端偏好设置配置。 */
    public AdminPreferenceConfiguration() {
    }

    /** @return Vben 管理端偏好设置定义 */
    @Bean
    PreferenceSetting<JsonNode> adminUiPreferenceSetting() {
        return UI_PREFERENCES;
    }
}
