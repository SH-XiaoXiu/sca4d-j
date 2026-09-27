package com.sca4d.core.api.setting.exception;

import org.jetbrains.annotations.NotNull;

/** 表示管理端尝试修改仍由初始化节点 ENV 持续同步的设置。 */
public final class SettingReadOnlyException extends IllegalStateException {

    private final String settingKey;

    /**
     * 创建设置只读异常。
     *
     * @param settingKey 当前由初始化节点持续同步的设置键
     */
    public SettingReadOnlyException(@NotNull String settingKey) {
        super("设置仍由初始化节点 ENV 同步: " + settingKey);
        this.settingKey = settingKey;
    }

    /** @return 当前由初始化节点持续同步的设置键 */
    public @NotNull String settingKey() {
        return settingKey;
    }
}
