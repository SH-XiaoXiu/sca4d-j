package com.sca4d.core.api.version;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;

/** 统一兼容解析的结果；路由和同路径适配共用此结果。 */
public final class CompatibilityDecision {
    private final CompatibilityRule rule;

    private final CompatibilityStatus status;

    private final String reason;

    /** 创建不包含请求原文的安全决策。 */
    public CompatibilityDecision(CompatibilityStatus status, @Nullable CompatibilityRule rule, String reason) {
        this.status = status;
        this.rule = rule;
        this.reason = reason;
    }

    /** @return 兼容状态 */
    @JsonProperty
    public CompatibilityStatus status() {
        return status;
    }

    /** @return 选中的契约；不匹配时为空 */
    @JsonProperty
    public @Nullable CompatibilityRule rule() {
        return rule;
    }

    /** @return 稳定的原因标识 */
    @JsonProperty
    public String reason() {
        return reason;
    }

    /** @return 调用方必须使用的契约；没有选中规则时抛出异常 */
    public int contract() {
        if (rule == null || status == CompatibilityStatus.BLOCKED)
            throw new IllegalStateException("没有可执行的契约");
        return rule.contract();
    }
}
