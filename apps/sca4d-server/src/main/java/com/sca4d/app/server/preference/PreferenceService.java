package com.sca4d.app.server.preference;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.PreferenceSetting;
import com.sca4d.core.api.setting.PreferenceSettings;
import com.sca4d.core.api.setting.SettingOwner;
import com.sca4d.core.api.setting.SettingValue;
import com.sca4d.core.spring.setting.SettingCatalog;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 按当前 Platform Principal 读取、替换和恢复已注册的个人偏好。 */
@Service
public final class PreferenceService {

    private static final Logger LOG = LoggerFactory.getLogger(PreferenceService.class);

    private static final @NotNull TreeCode PLATFORM_PRINCIPAL_OWNER = TreeCode.ofSegment("PLATFORM_PRINCIPAL");

    private final SettingCatalog catalog;

    private final PreferenceSettings settings;

    /** 创建个人偏好应用服务。 */
    public PreferenceService(@NotNull SettingCatalog catalog, @NotNull PreferenceSettings settings) {
        this.catalog = Objects.requireNonNull(catalog, "设置目录不能为空");
        this.settings = Objects.requireNonNull(settings, "PreferenceSettings 不能为空");
    }

    /** @return 当前 Principal 的已注册偏好值、来源和版本 */
    public @NotNull SettingValue<?> inspect(long principalId, @NotNull String key) {
        return inspectValue(settingByKey(key), owner(principalId));
    }

    /** 使用偏好定义的 Codec 校验并完整替换当前 Principal 的 JSON 值。 */
    public @NotNull SettingValue<?> replace(
            long principalId,
            @NotNull String key,
            @NotNull JsonNode value) {
        SettingValue<?> replaced = replaceValue(
                settingByKey(key),
                owner(principalId),
                Objects.requireNonNull(value, "偏好值不能为空"));
        LOG.debug(
                "个人偏好已完整替换 | principalId={} settingKey={} version={}",
                principalId,
                replaced.setting().key(),
                replaced.version().value());
        return replaced;
    }

    /** 删除当前 Principal 的偏好覆盖值并保留递增版本。 */
    public @NotNull SettingValue<?> reset(long principalId, @NotNull String key) {
        SettingValue<?> reset = settings.reset(settingByKey(key), owner(principalId));
        LOG.debug(
                "个人偏好已恢复默认值 | principalId={} settingKey={} version={}",
                principalId,
                reset.setting().key(),
                reset.version().value());
        return reset;
    }

    /** 返回必须存在的偏好设置定义。 */
    private @NotNull PreferenceSetting<?> settingByKey(@NotNull String key) {
        return catalog.findPreference(Objects.requireNonNull(key, "偏好键不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("偏好设置不存在: " + key));
    }

    /** 捕获偏好定义泛型并读取当前值。 */
    private <T> @NotNull SettingValue<T> inspectValue(
            @NotNull PreferenceSetting<T> setting,
            @NotNull SettingOwner owner) {
        return settings.inspect(setting, owner);
    }

    /** 捕获偏好定义泛型，使用 Codec 解码 JSON 后保存值。 */
    private <T> @NotNull SettingValue<T> replaceValue(
            @NotNull PreferenceSetting<T> setting,
            @NotNull SettingOwner owner,
            @NotNull JsonNode value) {
        return settings.replace(setting, owner, setting.codec().decodeJson(value));
    }

    /** 返回当前 Platform Principal 对应的统一设置 owner。 */
    private static @NotNull SettingOwner owner(long principalId) {
        if (principalId <= 0) {
            throw new IllegalArgumentException("Principal 标识必须为正数");
        }
        return new SettingOwner(PLATFORM_PRINCIPAL_OWNER, Long.toString(principalId));
    }
}
