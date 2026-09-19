package com.sca4d.audit.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 配置审计 payload 的最大字节数和需要递归掩码的字段名片段。 */
@ConfigurationProperties("sca4dj.audit")
public class AuditProperties {
    private int maxPayloadBytes = 65_536;

    private @NotNull List<@NotNull String> sensitiveFields = new ArrayList<>(
            List.of("password", "token", "secret", "salt", "authorization"));

    /**
     * 返回单条 payload 允许保存的最大 UTF-8 字节数。
     *
     * @return 正数字节数
     */
    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    /**
     * 设置单条 payload 允许保存的最大 UTF-8 字节数。
     *
     * @param maxPayloadBytes 正数字节数
     */
    public void setMaxPayloadBytes(int maxPayloadBytes) {
        if (maxPayloadBytes <= 0) {
            throw new IllegalArgumentException("审计 payload 最大字节数必须为正数");
        }
        this.maxPayloadBytes = maxPayloadBytes;
    }

    /**
     * 返回不区分大小写匹配的敏感字段片段。
     *
     * @return 不可修改的字段片段列表
     */
    public @NotNull @Unmodifiable List<@NotNull String> getSensitiveFields() {
        return List.copyOf(sensitiveFields);
    }

    /**
     * 设置不区分大小写匹配的敏感字段片段。
     *
     * @param sensitiveFields 非空白字段片段
     * @throws NullPointerException 列表或其中的片段为空时抛出
     * @throws IllegalArgumentException 列表包含空白片段时抛出
     */
    public void setSensitiveFields(@NotNull List<@NotNull String> sensitiveFields) {
        List<String> checked = new ArrayList<>();
        for (String value : Objects.requireNonNull(sensitiveFields, "敏感字段列表不能为空")) {
            String normalized = Objects.requireNonNull(value, "敏感字段不能为空").trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("敏感字段不能为空白");
            }
            checked.add(normalized);
        }
        this.sensitiveFields = checked;
    }
}
