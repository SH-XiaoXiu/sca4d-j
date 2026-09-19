package com.sca4d.core.spring.setting;

import com.sca4d.core.api.setting.PreferenceSetting;
import com.sca4d.core.api.setting.Setting;
import com.sca4d.core.api.setting.SettingGroup;
import com.sca4d.core.api.setting.SystemSetting;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** 校验并保存当前应用注册的全部 Setting 定义。 */
public final class SettingCatalog {

    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+");

    private static final Pattern GROUP_KEY = Pattern.compile("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*");

    private static final Pattern ENV = Pattern.compile("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+");

    private final Map<String, Setting<?>> settings;

    private final List<SystemSetting<?>> systemSettings;

    /**
     * 创建设置目录并拒绝重复 key、重复 SystemSetting ENV 和冲突分组。
     *
     * @param definitions Spring 容器中注册的全部 Setting Bean
     */
    public SettingCatalog(@NotNull Collection<? extends Setting<?>> definitions) {
        Map<String, Setting<?>> byKey = new LinkedHashMap<>();
        Map<String, SystemSetting<?>> byEnvironment = new LinkedHashMap<>();
        Map<String, SettingGroup> byGroup = new LinkedHashMap<>();
        for (Setting<?> definition : Objects.requireNonNull(definitions, "设置定义不能为空")) {
            if (!KEY.matcher(definition.key()).matches()) {
                throw new IllegalArgumentException("设置键格式错误: " + definition.key());
            }
            Setting<?> duplicate = byKey.putIfAbsent(definition.key(), definition);
            if (duplicate != null) {
                throw new IllegalArgumentException("重复设置键: " + definition.key());
            }
            if (definition instanceof SystemSetting<?> system) {
                validateSystemSetting(system, byEnvironment, byGroup);
            }
        }
        this.settings = Map.copyOf(byKey);
        List<SystemSetting<?>> orderedSystemSettings = new ArrayList<>();
        for (Setting<?> setting : byKey.values()) {
            if (setting instanceof SystemSetting<?> system) {
                orderedSystemSettings.add(system);
            }
        }
        orderedSystemSettings.sort(
                Comparator.comparingInt((SystemSetting<?> value) -> value.group().sortOrder())
                        .thenComparing(value -> value.group().key())
                        .thenComparingInt(SystemSetting::sortOrder)
                        .thenComparing(SystemSetting::key));
        this.systemSettings = List.copyOf(orderedSystemSettings);
    }

    /** @return 按分组和设置顺序排列的全部 SystemSetting */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull SystemSetting<?>> systemSettings() {
        return systemSettings;
    }

    /** @return 指定键的 SystemSetting；未注册或类型不匹配时返回空 */
    @CheckReturnValue
    public @NotNull Optional<SystemSetting<?>> findSystem(@NotNull String key) {
        Setting<?> setting = settings.get(Objects.requireNonNull(key, "设置键不能为空"));
        return setting instanceof SystemSetting<?> system ? Optional.of(system) : Optional.empty();
    }

    /** @return 指定键的 PreferenceSetting；未注册或类型不匹配时返回空 */
    @CheckReturnValue
    public @NotNull Optional<PreferenceSetting<?>> findPreference(@NotNull String key) {
        Setting<?> setting = settings.get(Objects.requireNonNull(key, "设置键不能为空"));
        return setting instanceof PreferenceSetting<?> preference ? Optional.of(preference) : Optional.empty();
    }

    /** 校验调用方使用的是目录中注册的同一个设置对象。 */
    public void assertRegistered(@NotNull Setting<?> setting) {
        Setting<?> checked = Objects.requireNonNull(setting, "设置定义不能为空");
        if (settings.get(checked.key()) != checked) {
            throw new IllegalArgumentException("设置未在当前应用注册: " + checked.key());
        }
    }

    /** 校验 SystemSetting 特有的分组和 ENV 元数据。 */
    private static void validateSystemSetting(
            @NotNull SystemSetting<?> definition,
            @NotNull Map<String, SystemSetting<?>> byEnvironment,
            @NotNull Map<String, SettingGroup> byGroup) {
        if (!GROUP_KEY.matcher(definition.group().key()).matches()) {
            throw new IllegalArgumentException("设置分组键格式错误: " + definition.group().key());
        }
        if (!ENV.matcher(definition.environmentVariable()).matches()) {
            throw new IllegalArgumentException("设置初始化 ENV 格式错误: " + definition.environmentVariable());
        }
        SystemSetting<?> duplicate = byEnvironment.putIfAbsent(definition.environmentVariable(), definition);
        if (duplicate != null) {
            throw new IllegalArgumentException("重复设置初始化 ENV: " + definition.environmentVariable());
        }
        SettingGroup existingGroup = byGroup.putIfAbsent(definition.group().key(), definition.group());
        if (existingGroup != null
                && (!existingGroup.labelKey().equals(definition.group().labelKey())
                        || existingGroup.sortOrder() != definition.group().sortOrder())) {
            throw new IllegalArgumentException("同一设置分组使用了冲突定义: " + definition.group().key());
        }
    }
}
