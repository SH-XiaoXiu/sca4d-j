package com.sca4d.app.server.preference.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.setting.SettingValue;
import com.sca4d.core.spring.web.annotation.ETag;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 返回一个已注册个人偏好的结构版本、完整 JSON 值和维护状态。 */
@SuppressWarnings("unused")
public final class PreferenceView {

    private final String key;

    private final int schemaVersion;

    private final JsonNode value;

    private final boolean stored;

    private final @NotNull Version revision;

    private PreferenceView(@NotNull SettingValue<?> preference) {
        SettingValue<?> checked = Objects.requireNonNull(preference, "偏好设置值不能为空");
        this.key = checked.setting().key();
        this.schemaVersion = checked.setting().schemaVersion();
        this.value = encodeValue(checked);
        this.stored = checked.stored();
        this.revision = checked.version();
    }

    /** 从类型化设置值创建 HTTP 投影。 */
    public static @NotNull PreferenceView from(@NotNull SettingValue<?> preference) {
        return new PreferenceView(preference);
    }

    /** @return 已注册偏好键 */
    public @NotNull String getKey() {
        return key;
    }

    /** @return 当前偏好结构版本 */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return 当前 Principal 的完整偏好 JSON */
    public @NotNull JsonNode getValue() {
        return value;
    }

    /** @return 数据库中存在偏好覆盖值时返回 {@code true} */
    public boolean isStored() {
        return stored;
    }

    /** @return 生成当前完整偏好表示的版本 */
    @ETag
    public @NotNull Version revision() {
        return revision;
    }

    /** 捕获偏好值泛型并编码为独立 JSON 节点。 */
    private static <T> @NotNull JsonNode encodeValue(@NotNull SettingValue<T> preference) {
        return preference.setting().codec().encodeJson(preference.value());
    }
}
