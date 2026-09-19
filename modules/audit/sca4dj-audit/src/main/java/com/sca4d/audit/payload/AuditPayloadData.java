package com.sca4d.audit.payload;

import com.sca4d.core.api.audit.AuditPayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** 返回审计 payload 的持久化类型、版本、原始 JSON 和可选强类型结果。 */
public final class AuditPayloadData {
    private final @NotNull String type;

    private final int version;

    private final @NotNull String json;

    private final @Nullable AuditPayload decoded;

    /**
     * 创建审计 payload 查询结果。
     *
     * @param type 持久化载荷类型
     * @param version 正整数 JSON 结构版本
     * @param json 已脱敏保存的原始 JSON
     * @param decoded 当前应用存在匹配 Codec 时的强类型结果
     * @throws NullPointerException type 或 json 为空时抛出
     * @throws IllegalArgumentException type 或 json 为空白文本，或 version 不是正数时抛出
     */
    public AuditPayloadData(
            @NotNull String type,
            int version,
            @NotNull String json,
            @Nullable AuditPayload decoded) {
        String checkedType = Objects.requireNonNull(type, "payload 类型不能为空");
        if (checkedType.isBlank()) {
            throw new IllegalArgumentException("payload 类型不能为空白");
        }
        this.type = checkedType;
        if (version <= 0) {
            throw new IllegalArgumentException("payload 版本必须为正数");
        }
        this.version = version;
        String checkedJson = Objects.requireNonNull(json, "payload JSON 不能为空");
        if (checkedJson.isBlank()) {
            throw new IllegalArgumentException("payload JSON 不能为空白");
        }
        this.json = checkedJson;
        this.decoded = decoded;
    }

    /** @return 持久化载荷类型 */
    public @NotNull String type() {
        return type;
    }

    /** @return 正整数 JSON 结构版本 */
    public int version() {
        return version;
    }

    /** @return 已脱敏保存的原始 JSON */
    public @NotNull String json() {
        return json;
    }

    /** @return 当前应用存在匹配 Codec 时的强类型载荷 */
    public @NotNull Optional<AuditPayload> decoded() {
        return Optional.ofNullable(decoded);
    }
}
