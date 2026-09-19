package com.sca4d.core.api.setting;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/** 提供不带进程缓存的类型化设置读取和数据库修改。 */
public interface Settings {

    /**
     * 每次调用都从数据库读取一个设置；数据库没有值时返回 Java 默认值。
     *
     * @param setting 设置定义
     * @param <T> 设置值类型
     * @return 当前数据库值或 Java 默认值
     */
    @CheckReturnValue
    <T> @NotNull T get(@NotNull SystemSetting<T> setting);

    /** @return 当前系统设置值、来源和版本 */
    @CheckReturnValue
    <T> @NotNull SettingValue<T> inspect(@NotNull SystemSetting<T> setting);

    /**
     * 使用一次数据库查询读取多个设置并返回不可变快照。
     *
     * @param settings 需要同时读取的设置定义
     * @return 同一次查询得到的设置快照
     */
    @CheckReturnValue
    @NotNull
    SettingValues get(@NotNull Collection<? extends SystemSetting<?>> settings);

    /** @return 指定 owner 的后端逻辑设置值或定义默认值 */
    @CheckReturnValue
    <T> @NotNull T get(@NotNull LogicSetting<T> setting, @NotNull SettingOwner owner);

    /** @return 指定 owner 的后端逻辑设置值、来源和版本 */
    @CheckReturnValue
    <T> @NotNull SettingValue<T> inspect(@NotNull LogicSetting<T> setting, @NotNull SettingOwner owner);

    /**
     * 覆盖一个数据库设置值。
     *
     * @param setting 设置定义
     * @param value 新值
     * @param <T> 设置值类型
     * @throws SettingReadOnlyException 设置仍由初始化节点 ENV 同步时抛出
     */
    <T> @NotNull SettingValue<T> replace(@NotNull SystemSetting<T> setting, @NotNull T value);

    /** 完整替换指定 owner 的后端逻辑设置。 */
    <T> @NotNull SettingValue<T> replace(
            @NotNull LogicSetting<T> setting,
            @NotNull SettingOwner owner,
            @NotNull T value);

    /**
     * 删除数据库值，使后续读取恢复 Java 默认值。
     *
     * @param setting 设置定义
     * @throws SettingReadOnlyException 设置仍由初始化节点 ENV 同步时抛出
     */
    @NotNull
    SettingValue<?> reset(@NotNull SystemSetting<?> setting);

    /** 删除指定 owner 的逻辑设置覆盖值并保留递增版本。 */
    @NotNull
    SettingValue<?> reset(@NotNull LogicSetting<?> setting, @NotNull SettingOwner owner);

    /** 在 owner 已删除时物理删除指定逻辑设置行。 */
    void erase(@NotNull LogicSetting<?> setting, @NotNull SettingOwner owner);
}
