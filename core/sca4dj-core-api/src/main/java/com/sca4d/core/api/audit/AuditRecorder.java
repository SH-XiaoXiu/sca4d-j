package com.sca4d.core.api.audit;

import com.sca4d.core.api.annotation.Audit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 按明确方式记录审计；具体实现决定返回时完成写入、可靠交接或提交后尽力登记。 */
public interface AuditRecorder {

    /** 返回本实现的记录方式；默认同步执行，不能返回仅用于注解继承的 DEFAULT。 */
    @Contract(pure = true)
    default @NotNull Audit.Mode mode() {
        return Audit.Mode.DIRECT;
    }

    /**
     * 执行本实现声明的写入或交接；尽力模式必须自行记录丢弃和后台失败。
     *
     * @param record 审计记录
     */
    void record(@NotNull AuditRecord record);
}
