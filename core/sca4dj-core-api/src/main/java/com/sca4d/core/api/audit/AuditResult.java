package com.sca4d.core.api.audit;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 审计操作记录的结果分类。 */
public enum AuditResult implements BaseEnum {
    /** 操作成功完成。 */
    SUCCESS("成功"),
    /** 操作被业务规则或授权策略拒绝。 */
    DENIED("拒绝"),
    /** 操作执行失败。 */
    FAILED("失败");

    private final @NotNull String description;

    AuditResult(@NotNull String description) {
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
