package com.sca4d.common.resource;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 根据当前请求权限和资源是否存在计算出的详情页导航状态。 */
public enum ResourceNavigationState implements BaseEnum {
    /** 当前请求可以进入资源详情。 */
    NAVIGABLE("NAVIGABLE", "可导航"),
    /** 资源存在，但当前请求无详情权限。 */
    FORBIDDEN("FORBIDDEN", "无详情权限"),
    /** 资源已经删除或不再可见。 */
    DELETED("DELETED", "资源已删除"),
    /** 当前应用没有注册该资源类型的详情页。 */
    UNSUPPORTED("UNSUPPORTED", "无详情能力");

    private final String value;
    private final String description;

    ResourceNavigationState(@NotNull String value, @NotNull String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
