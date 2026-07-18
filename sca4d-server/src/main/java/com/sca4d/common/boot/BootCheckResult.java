package com.sca4d.common.boot;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** 启动或健康检查结果。 */
@Getter
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class BootCheckResult {

    private final boolean available;
    private final @NotNull String message;

    /**
     * 创建可用结果。
     *
     * @param message 对当前状态的简要说明
     * @return 可用结果
     */
    public static @NotNull BootCheckResult available(@NotNull String message) {
        return new BootCheckResult(true, message);
    }

    /**
     * 创建不可用结果。
     *
     * @param message 不可用原因，不得包含凭据
     * @return 不可用结果
     */
    public static @NotNull BootCheckResult unavailable(@NotNull String message) {
        return new BootCheckResult(false, message);
    }
}
