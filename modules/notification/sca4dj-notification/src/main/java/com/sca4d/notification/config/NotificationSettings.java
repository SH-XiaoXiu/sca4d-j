package com.sca4d.notification.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.LogicSetting;
import com.sca4d.core.api.setting.SettingCodec;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.SystemSetting;
import org.jetbrains.annotations.NotNull;

/** 声明 Notification 框架内部保存的系统渠道默认值和接收人覆盖值。 */
public final class NotificationSettings {

    private static final SettingGroup GROUP = new SettingGroup("notification", "setting.group.notification", 500);

    /** 管理员在通知类型 Java 初值之上设置的渠道默认值。 */
    public static final @NotNull SystemSetting<JsonNode> CHANNEL_DEFAULTS = new SystemSetting<>(
            "notification.channel-defaults",
            1,
            GROUP,
            "setting.notification.channel-defaults",
            "setting.notification.channel-defaults.description",
            "SCA4DJ_NOTIFICATION_CHANNEL_DEFAULTS",
            JsonNodeFactory.instance.objectNode(),
            SettingCodec.jsonObject(65_536),
            Pipeline.identity(JsonNode.class),
            false,
            100);

    /** 接收人在系统默认值之上保存的可选渠道覆盖值。 */
    public static final @NotNull LogicSetting<JsonNode> CHANNEL_PREFERENCES = new LogicSetting<>(
            "notification.channel-preferences",
            1,
            JsonNodeFactory.instance.objectNode(),
            SettingCodec.jsonObject(65_536),
            Pipeline.identity(JsonNode.class),
            false);

    private NotificationSettings() {
    }
}
