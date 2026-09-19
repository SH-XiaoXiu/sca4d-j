package com.sca4d.core.api.setting;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 标识管理端中一起展示的一组设置。 */
public final class SettingGroup {

    private final String key;

    private final String labelKey;

    private final int sortOrder;

    /**
     * 创建设置分组。
     *
     * @param key 全局唯一的点分小写分组键
     * @param labelKey 分组名称的国际化消息键
     * @param sortOrder 管理端展示顺序
     */
    public SettingGroup(@NotNull String key, @NotNull String labelKey, int sortOrder) {
        this.key = TextUtils.requireText(key, "设置分组键");
        this.labelKey = TextUtils.requireText(labelKey, "设置分组标签消息键");
        this.sortOrder = sortOrder;
    }

    /** @return 全局唯一的分组键 */
    @Contract(pure = true)
    public @NotNull String key() {
        return key;
    }

    /** @return 分组名称的国际化消息键 */
    @Contract(pure = true)
    public @NotNull String labelKey() {
        return labelKey;
    }

    /** @return 管理端展示顺序 */
    @Contract(pure = true)
    public int sortOrder() {
        return sortOrder;
    }
}
