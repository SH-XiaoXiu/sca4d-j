package com.sca4d.app.server.notification.request;

import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 接收一个通知设置的完整 JSON 值。 */
public class ReplaceNotificationSettingRequest {
    private @Nullable JsonNode value;

    public @NotNull JsonNode getValue() {
        return Objects.requireNonNull(value, "通知设置值不能为空").deepCopy();
    }

    public void setValue(@Nullable JsonNode value) {
        this.value = value == null ? null : value.deepCopy();
    }
}
