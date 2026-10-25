package com.sca4d.core.api.version.exception;

import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.version.CompatibilityDecision;
import org.jetbrains.annotations.Nullable;

/** 版本声明畸形或矩阵拒绝当前契约；不包含请求头原文。 */
public final class VersionCompatibilityException extends BusinessException {
    private final CompatibilityDecision decision;

    /** @param decision 不兼容决策；为空表示输入格式错误 */
    public VersionCompatibilityException(@Nullable CompatibilityDecision decision) {
        super(decision == null ? CoreErrorCode.CLIENT_VERSION_INVALID : CoreErrorCode.CLIENT_INCOMPATIBLE);
        this.decision = decision;
    }

    /** @return 可公开的兼容决策 */
    public @Nullable CompatibilityDecision decision() {
        return decision;
    }
}
