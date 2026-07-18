package com.sca4d.common.audit;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 审计记录执行模式。 */
public enum AuditMode implements BaseEnum {

    /** 继承全局审计模式，仅用于注解声明。 */
    DEFAULT("继承全局配置"),

    /** 请求完成后异步独立落库。 */
    ASYNC("异步独立落库"),

    /** 成功审计与业务在同一事务同步落库。 */
    SYNC("同步事务落库");

    private final String value;
    private final String description;

    AuditMode(@NotNull String description) {
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
