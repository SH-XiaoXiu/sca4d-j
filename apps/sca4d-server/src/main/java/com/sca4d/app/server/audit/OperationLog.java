package com.sca4d.app.server.audit;

import com.sca4d.audit.query.AuditEntry;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 组合一条 Audit 记录与 Sca4d Server 用户资料中的 Principal 展示名称。 */
public final class OperationLog {
    private final @NotNull AuditEntry entry;

    private final @Nullable String principalDisplayName;

    /**
     * 创建管理端可读的操作日志。
     *
     * @param entry Audit 模块返回的历史记录
     * @param principalDisplayName 可选 Principal 展示名称；资料已不存在时为空
     */
    public OperationLog(@NotNull AuditEntry entry, @Nullable String principalDisplayName) {
        this.entry = Objects.requireNonNull(entry, "审计记录不能为空");
        this.principalDisplayName = principalDisplayName;
    }

    /** @return Audit 模块返回的历史记录 */
    @Contract(pure = true)
    public @NotNull AuditEntry entry() {
        return entry;
    }

    /** @return 可选 Principal 展示名称 */
    @Contract(pure = true)
    public @Nullable String principalDisplayName() {
        return principalDisplayName;
    }
}
