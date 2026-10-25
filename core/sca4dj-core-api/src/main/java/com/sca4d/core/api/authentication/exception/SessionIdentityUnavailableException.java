package com.sca4d.core.api.authentication.exception;

import org.jetbrains.annotations.NotNull;

import java.io.Serial;

/** 表示 Account 当前状态或身份关系已经不能恢复为有效 Session。 */
public class SessionIdentityUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 使用所属身份模块返回的失败创建异常。
     *
     * @param cause 身份状态或关系无效的原始失败
     */
    public SessionIdentityUnavailableException(@NotNull Throwable cause) {
        super("Session 身份已经不可用", cause);
    }
}
