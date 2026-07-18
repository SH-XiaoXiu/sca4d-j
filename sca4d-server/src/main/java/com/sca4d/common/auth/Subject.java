package com.sca4d.common.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 当前登录身份。
 *
 * <p>Subject 表示一次认证结果背后的登录身份，通常对应自然人、系统账号或外部账号映射。
 */
public class Subject {

    private Long subjectId;
    private String displayName;

    public Subject() {
    }

    /**
     * 创建登录身份上下文。
     *
     * @param subjectId Subject 主键
     * @param displayName 展示名称
     */
    public Subject(@NotNull Long subjectId, @Nullable String displayName) {
        this.subjectId = subjectId;
        this.displayName = displayName;
    }

    public @NotNull Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(@NotNull Long subjectId) {
        this.subjectId = subjectId;
    }

    public @Nullable String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
    }
}
