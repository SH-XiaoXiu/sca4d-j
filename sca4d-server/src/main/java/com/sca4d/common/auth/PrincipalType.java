package com.sca4d.common.auth;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/**
 * 当前执行主体类型。
 *
 * <p>业务模块新增主体类型时，必须同时提供默认主体选择规则和测试。
 */
public enum PrincipalType implements BaseEnum {
    /** 系统主体。 */
    SYSTEM("SYSTEM", "系统"),
    /** 平台主体。 */
    PLATFORM("PLATFORM", "平台"),
    /** 租户内执行主体。 */
    TENANT("TENANT", "租户成员"),
    /** 默认业务主体。 */
    DEFAULT("DEFAULT", "默认主体");

    private final String value;
    private final String description;

    PrincipalType(@NotNull String value, @NotNull String description) {
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

    /**
     * 默认主体类型。
     *
     * @return 默认主体类型
     */
    public static @NotNull PrincipalType defaultType() {
        return DEFAULT;
    }
}
