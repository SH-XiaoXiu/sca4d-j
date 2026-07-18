package com.sca4d.authentication;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 一次认证最终允许完成的操作。 */
public enum AuthenticationPurpose implements BaseEnum {
    /** 创建 Account 登录 Session。 */
    LOGIN("LOGIN", "登录"),
    /** 创建 Subject、Account 和初始认证材料。 */
    REGISTRATION("REGISTRATION", "注册"),
    /** 为已认证 Account 绑定认证方式。 */
    LINKING("LINKING", "绑定"),
    /** 为敏感操作补充一次认证证明。 */
    STEP_UP("STEP_UP", "二次验证"),
    /** 使用恢复材料恢复 Account。 */
    RECOVERY("RECOVERY", "恢复");

    private final String value;
    private final String description;

    AuthenticationPurpose(@NotNull String value, @NotNull String description) {
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
