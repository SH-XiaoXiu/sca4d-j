package com.sca4d.notification.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.codec.SettingCodec;
import com.sca4d.core.api.setting.definition.LogicSetting;
import com.sca4d.core.api.setting.definition.SystemSetting;
import org.jetbrains.annotations.NotNull;

/** 声明 Notification 框架内部保存的系统渠道默认值和接收人覆盖值。 */
public final class NotificationSettings {

    private static final SettingGroup GROUP = new SettingGroup(
            TreeCode.of("NOTIFICATION"),
            "setting.group.notification",
            500);

    /** 管理员在通知类型 Java 初值之上设置的渠道默认值。 */
    public final @NotNull SystemSetting<JsonNode> CHANNEL_DEFAULTS;

    /** 接收人在系统默认值之上保存的可选渠道覆盖值。 */
    public final @NotNull LogicSetting<JsonNode> CHANNEL_PREFERENCES;

    /** 构造模块设置，每个应用上下文独立保存 Source。 */
    public NotificationSettings(@NotNull SettingReader<String> reader, SettingWriter<String> writer) {
        this.CHANNEL_DEFAULTS = new SystemSetting<>(
                reader,
                writer,
                TreeCode.of("NOTIFICATION:CHANNEL_DEFAULTS"),
                GROUP,
                "setting.notification.channel-defaults",
                "setting.notification.channel-defaults.description",
                "SCA4DJ_NOTIFICATION_CHANNEL_DEFAULTS",
                JsonNodeFactory.instance.objectNode(),
                SettingCodec.jsonObject(65_536),
                Pipeline.identity(JsonNode.class),
                false,
                100);
        this.CHANNEL_PREFERENCES = new LogicSetting<>(
                reader,
                writer,
                TreeCode.of("NOTIFICATION:CHANNEL_PREFERENCES"),
                JsonNodeFactory.instance.objectNode(),
                SettingCodec.jsonObject(65_536),
                Pipeline.identity(JsonNode.class),
                false);
    }
}
