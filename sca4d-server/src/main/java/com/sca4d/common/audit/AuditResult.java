package com.sca4d.common.audit;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/**
 * 审计执行结果。
 */
@CodeDictionary(code = "system.audit-result", nameKey = "dictionary.system.audit-result")
public enum AuditResult implements BaseEnum {
    /** 执行成功。 */
    SUCCESS("执行成功"),
    /** 执行失败。 */
    FAILED("执行失败");

    private final String value;
    private final String description;

    AuditResult(@NotNull String description) {
        this.value = BaseEnum.normalize(name());
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
