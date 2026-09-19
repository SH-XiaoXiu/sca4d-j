package com.sca4d.iot.api;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 标识 Device 所属 Product 定义来自数据库还是应用代码。 */
public enum ProductSource implements BaseEnum {
    /** Product 和物模型由数据库保存并允许管理端维护。 */
    DATABASE("数据库"),

    /** Product 和物模型由应用代码构造并且运行时只读。 */
    CODE("应用代码");

    private final @NotNull String description;

    ProductSource(@NotNull String description) {
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
