package com.sca4d.app.server.preference;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.definition.PreferenceSetting;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import com.sca4d.core.spring.setting.SettingCatalog;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 按当前 Platform Principal 读取、替换和恢复已注册的个人偏好。 */
@Service
public class PreferenceService {

    private static final Logger LOG = LoggerFactory.getLogger(PreferenceService.class);

    private static final @NotNull TreeCode PLATFORM_PRINCIPAL_OWNER = TreeCode.ofSegment("PLATFORM_PRINCIPAL");

    private final SettingCatalog catalog;

    /** 创建个人偏好应用服务。 */
    public PreferenceService(@NotNull SettingCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "设置目录不能为空");
    }

    /** @return 当前 Principal 的已注册偏好值、来源和版本 */
    public @NotNull SettingValue<JsonNode> inspect(long principalId, @NotNull TreeCode key) {
        return inspectValue(settingByKey(key), owner(principalId));
    }

    /** 使用偏好定义的 Codec 校验并完整替换当前 Principal 的 JSON 值。 */
    public @NotNull SettingValue<JsonNode> replace(
            long principalId,
            @NotNull TreeCode key,
            @NotNull JsonNode value) {
        SettingValue<JsonNode> replaced = replaceValue(
                settingByKey(key),
                owner(principalId),
                Objects.requireNonNull(value, "偏好值不能为空"));
        LOG.debug(
                "个人偏好已完整替换 | principalId={} settingKey={} version={}",
                principalId,
                replaced.code(),
                replaced.revision().orElseThrow().value());
        return replaced;
    }

    /** 删除当前 Principal 的偏好覆盖值并保留递增版本。 */
    public @NotNull SettingValue<JsonNode> reset(long principalId, @NotNull TreeCode key) {
        SettingValue<JsonNode> reset = resetValue(settingByKey(key), owner(principalId));
        LOG.debug(
                "个人偏好已恢复默认值 | principalId={} settingKey={} version={}",
                principalId,
                reset.code(),
                reset.revision().orElseThrow().value());
        return reset;
    }

    /** 返回必须存在的偏好设置定义。 */
    private @NotNull PreferenceSetting<?> settingByKey(@NotNull TreeCode key) {
        return catalog.findPreference(Objects.requireNonNull(key, "偏好编码不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("偏好设置不存在: " + key));
    }

    /** 捕获偏好定义泛型并读取当前值。 */
    private <T> @NotNull SettingValue<JsonNode> inspectValue(
            @NotNull PreferenceSetting<T> setting,
            @NotNull SettingOwner owner) {
        return setting.inspect(owner).map(setting.codec()::encodeJson);
    }

    /** 捕获偏好定义泛型，使用 Codec 解码 JSON 后保存值。 */
    private <T> @NotNull SettingValue<JsonNode> replaceValue(
            @NotNull PreferenceSetting<T> setting,
            @NotNull SettingOwner owner,
            @NotNull JsonNode value) {
        return setting.replace(owner, setting.codec().decodeJson(value)).map(setting.codec()::encodeJson);
    }

    /** 恢复偏好后按该定义的 Codec 返回完整 JSON 值。 */
    private <T> SettingValue<JsonNode> resetValue(PreferenceSetting<T> setting, SettingOwner owner) {
        return setting.reset(owner).map(setting.codec()::encodeJson);
    }

    /** 返回当前 Platform Principal 对应的统一设置 owner。 */
    private static @NotNull SettingOwner owner(long principalId) {
        if (principalId <= 0) {
            throw new IllegalArgumentException("Principal 标识必须为正数");
        }
        return new SettingOwner(PLATFORM_PRINCIPAL_OWNER, Long.toString(principalId));
    }
}
