package com.sca4d.core.api.setting;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/** 提供个人偏好完整读取、替换、恢复默认值和 owner 删除清理。 */
public interface PreferenceSettings {

    /** @return 指定 owner 的偏好值、来源和版本 */
    @CheckReturnValue
    <T> @NotNull SettingValue<T> inspect(
            @NotNull PreferenceSetting<T> setting,
            @NotNull SettingOwner owner);

    /** 完整替换指定 owner 的偏好。 */
    <T> @NotNull SettingValue<T> replace(
            @NotNull PreferenceSetting<T> setting,
            @NotNull SettingOwner owner,
            @NotNull T value);

    /** 删除指定 owner 的偏好覆盖值并保留递增版本。 */
    @NotNull
    SettingValue<?> reset(
            @NotNull PreferenceSetting<?> setting,
            @NotNull SettingOwner owner);

    /** 在 owner 已删除时物理删除指定偏好行。 */
    void erase(@NotNull PreferenceSetting<?> setting, @NotNull SettingOwner owner);
}
