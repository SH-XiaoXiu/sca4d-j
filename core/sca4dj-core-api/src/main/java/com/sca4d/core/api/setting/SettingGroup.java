package com.sca4d.core.api.setting;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 标识管理端中一起展示的一组设置。 */
public class SettingGroup {

    private final String labelKey;

    private final TreeCode code;

    private final int sortOrder;

    /**
     * 创建设置分组。
     *
     * @param code 全局唯一的稳定分组编码
     * @param labelKey 分组名称的国际化消息键
     * @param sortOrder 管理端展示顺序
     */
    public SettingGroup(@NotNull TreeCode code, @NotNull String labelKey, int sortOrder) {
        this.code = java.util.Objects.requireNonNull(code);
        if (code.hasNode(TreeCode.of("*")))
            throw new IllegalArgumentException("设置分组不能包含通配符");
        this.labelKey = TextUtils.requireText(labelKey, "设置分组标签消息键");
        this.sortOrder = sortOrder;
    }

    /** @return 全局唯一的分组键 */
    @Contract(pure = true)
    public @NotNull TreeCode code() {
        return code;
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
