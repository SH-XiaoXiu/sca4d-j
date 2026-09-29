package com.sca4d.core.api.audit;

import com.sca4d.core.api.annotation.Audit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 按明确方式记录审计；同步记录返回时完成当前事务内写入，队列记录返回时完成可靠交接提交。 */
public interface AuditRecorder {

    /** 返回本实现的记录方式；默认同步执行，不能返回仅用于注解继承的 DEFAULT。 */
    @Contract(pure = true)
    default @NotNull Audit.Mode mode() {
        return Audit.Mode.DIRECT;
    }

    /**
     * 完成本实现声明的写入或可靠交接；失败时抛出异常，不静默丢弃。
     *
     * @param record 审计记录
     */
    void record(@NotNull AuditRecord record);
}
