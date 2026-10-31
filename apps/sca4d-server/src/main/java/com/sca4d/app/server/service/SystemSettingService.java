package com.sca4d.app.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.app.server.system.ManagedSetting;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.definition.SystemSetting;
import com.sca4d.core.api.setting.exception.SettingReadOnlyException;
import com.sca4d.core.api.setting.value.SettingValue;
import com.sca4d.core.api.setting.value.SettingValues;
import com.sca4d.core.spring.setting.SettingCatalog;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** 管理当前应用注册的 SystemSetting，不允许请求创建未声明的任意键。 */
@Service
public class SystemSettingService {

    private static final Logger LOG = LoggerFactory.getLogger(SystemSettingService.class);

    private final SettingCatalog catalog;

    /** 创建系统设置应用服务。 */
    public SystemSettingService(@NotNull SettingCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "设置目录不能为空");
    }

    /** @return 按分组和设置顺序排列的全部管理结果 */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull ManagedSetting> findAll() {
        List<SystemSetting<?>> definitions = catalog.systemSettings();
        SettingValues values = new SettingValues(definitions.stream().map(SystemSetting::inspect).toList());
        return definitions.stream()
                .map(definition -> managedValue(definition, values))
                .toList();
    }

    /**
     * 使用设置自身 Codec 校验 JSON 后覆盖数据库值。
     *
     * @param key 已注册设置键
     * @param value 管理端提交的完整 JSON 值
     */
    public @NotNull SettingValue<?> replace(@NotNull TreeCode key, @NotNull JsonNode value) {
        SystemSetting<?> setting = settingByKey(key);
        try {
            SettingValue<?> replaced = replaceValue(setting, Objects.requireNonNull(value, "设置值不能为空"));
            LOG.info("系统设置已修改 | settingKey={} version={}", setting.code(), replaced.revision().orElseThrow().value());
            return replaced;
        } catch (SettingReadOnlyException exception) {
            throw new BusinessException(CoreErrorCode.CONFLICT, exception);
        }
    }

    /**
     * 删除数据库值，使之后的查询恢复 Java 默认值。
     *
     * @param key 已注册设置键
     */
    public @NotNull SettingValue<?> reset(@NotNull TreeCode key) {
        SystemSetting<?> setting = settingByKey(key);
        try {
            SettingValue<?> reset = setting.reset();
            LOG.info("系统设置已恢复默认值 | settingKey={} version={}", setting.code(), reset.revision().orElseThrow().value());
            return reset;
        } catch (SettingReadOnlyException exception) {
            throw new BusinessException(CoreErrorCode.CONFLICT, exception);
        }
    }

    /** 查找必须存在的设置定义。 */
    private @NotNull SystemSetting<?> settingByKey(@NotNull TreeCode key) {
        return catalog.findSystem(Objects.requireNonNull(key, "设置编码不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("设置不存在: " + key));
    }

    /** 捕获通配符类型，并通过设置 Codec 转换和保存文本。 */
    private <T> @NotNull SettingValue<T> replaceValue(
            @NotNull SystemSetting<T> setting,
            @NotNull JsonNode value) {
        return setting.replace(setting.codec().decodeJson(value));
    }

    /** 捕获设置定义的泛型，并读取对应的类型化查询值。 */
    private static <T> @NotNull ManagedSetting managedValue(
            @NotNull SystemSetting<T> setting,
            @NotNull SettingValues values) {
        return managed(setting, values.value(setting));
    }

    /** 把类型化查询值转换为不暴露敏感值的管理结果。 */
    private static <T> @NotNull ManagedSetting managed(
            @NotNull SystemSetting<T> setting,
            @NotNull SettingValue<T> value) {
        return new ManagedSetting(
                setting,
                setting.secret() ? null : setting.codec().encodeJson(value.value()),
                value.stored(),
                value.locked(),
                value.revision().orElseThrow());
    }
}
