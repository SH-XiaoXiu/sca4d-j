package com.sca4d.app.server.audit;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.sca4d.core.api.audit.AuditPayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.Objects;

/** 保存 Sca4d Server 明确选择、可以进入审计记录的非敏感字段。 */
public class AuditDetails implements AuditPayload {
    private final @NotNull @Unmodifiable Map<@NotNull String, @NotNull Object> values;

    /**
     * 创建只包含安全摘要字段的审计 payload。
     *
     * @param values 由具体解析器选择的非敏感字段
     */
    public AuditDetails(@NotNull Map<@NotNull String, @NotNull Object> values) {
        this.values = Map.copyOf(Objects.requireNonNull(values, "审计详情不能为空"));
    }

    /**
     * 返回直接写入 payload 根对象的安全字段。
     *
     * @return 不可修改的安全字段
     */
    @JsonAnyGetter
    public @NotNull @Unmodifiable Map<@NotNull String, @NotNull Object> getValues() {
        return values;
    }
}
