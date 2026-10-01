package com.sca4d.audit.payload;

import com.sca4d.core.api.audit.AuditPayload;
import com.sca4d.core.api.audit.AuditPayloadCodec;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 校验并索引应用注册的审计 payload Codec。 */
@ApiStatus.Internal
public class AuditPayloadRegistry {
    private final @NotNull Map<@NotNull String, @NotNull AuditPayloadCodec<?>> persisted = new LinkedHashMap<>();

    private final @NotNull Map<@NotNull Class<? extends AuditPayload>, @NotNull AuditPayloadCodec<?>> writable = new LinkedHashMap<>();

    /** 创建拒绝重复持久化键和运行时载荷类型的注册表。 */
    public AuditPayloadRegistry(@NotNull Collection<@NotNull AuditPayloadCodec<?>> codecs) {
        for (AuditPayloadCodec<?> codec : Objects.requireNonNull(codecs, "审计 Codec 不能为空")) {
            AuditPayloadCodec<?> checked = Objects.requireNonNull(codec, "审计 Codec 不能包含空值");
            String declaredType = Objects.requireNonNull(checked.payloadType(), "审计 Codec 类型不能为空");
            String type = declaredType.trim();
            if (type.isEmpty() || type.length() > 128 || !type.equals(declaredType) || "raw".equals(type)) {
                throw new IllegalStateException("审计 Codec 类型长度必须在 1 到 128 之间、不能包含首尾空白或使用保留值 raw");
            }
            if (checked.version() <= 0) {
                throw new IllegalStateException("审计 Codec 版本必须为正数: " + type);
            }
            Class<? extends AuditPayload> payloadClass = Objects.requireNonNull(
                    checked.payloadClass(),
                    "审计 Codec 载荷类不能为空");
            String key = key(type, checked.version());
            if (persisted.putIfAbsent(key, checked) != null) {
                throw new IllegalStateException("重复的审计 Codec: " + key);
            }
            if (writable.putIfAbsent(payloadClass, checked) != null) {
                throw new IllegalStateException("重复的审计载荷类: " + payloadClass.getName());
            }
        }
    }

    /** 按精确运行时类查找写入 Codec。 */
    @Nullable
    AuditPayloadCodec<?> find(@NotNull AuditPayload payload) {
        return writable.get(payload.getClass());
    }

    /** 按持久化类型和版本查找读取 Codec。 */
    @Nullable
    AuditPayloadCodec<?> find(@NotNull String type, int version) {
        return persisted.get(key(type, version));
    }

    /** 组合持久化类型和版本，生成注册表内部唯一键。 */
    private static @NotNull String key(@NotNull String type, int version) {
        return type + ":" + version;
    }
}
