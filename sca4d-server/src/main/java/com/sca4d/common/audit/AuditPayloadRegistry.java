package com.sca4d.common.audit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 审计场景载荷编解码器注册表。
 */
public class AuditPayloadRegistry {

    private final Map<String, AuditPayloadCodec<?>> codecs = new LinkedHashMap<>();
    private final Map<Class<? extends AuditPayload>, AuditPayloadCodec<?>> payloadCodecs = new LinkedHashMap<>();

    /**
     * 创建审计载荷 Codec 注册表并拒绝所有歧义扩展点。
     *
     * @param codecs 业务与公共组件模块提供的 Codec
     */
    public AuditPayloadRegistry(@NotNull Collection<AuditPayloadCodec<?>> codecs) {
        for (AuditPayloadCodec<?> codec : codecs) {
            AuditPayloadCodec<?> candidate = Objects.requireNonNull(codec, "审计载荷 Codec 不能为空");
            String payloadType = Objects.requireNonNull(
                    candidate.payloadType(), "审计载荷 Codec 类型不能为空");
            if (payloadType.isBlank()) {
                throw new IllegalStateException("审计载荷 Codec 类型不能为空");
            }
            if (!payloadType.equals(payloadType.trim())) {
                throw new IllegalStateException("审计载荷 Codec 类型不能包含首尾空白: " + payloadType);
            }
            if (candidate.payloadVersion() <= 0) {
                throw new IllegalStateException("审计载荷 Codec 版本必须大于 0: " + payloadType);
            }
            Class<? extends AuditPayload> payloadClass = Objects.requireNonNull(
                    candidate.payloadClass(), "审计载荷 Codec payload class 不能为空");
            String key = key(payloadType, candidate.payloadVersion());
            if (this.codecs.containsKey(key)) {
                throw new IllegalStateException("重复的审计载荷 Codec: " + key);
            }
            AuditPayloadCodec<?> existing = payloadCodecs.putIfAbsent(payloadClass, candidate);
            if (existing != null) {
                throw new IllegalStateException("重复的审计载荷 payload class: " + payloadClass.getName());
            }
            this.codecs.put(key, candidate);
        }
    }

    /**
     * 按固定类型和版本查找解码 Codec。
     *
     * @param payloadType 持久化的载荷类型
     * @param payloadVersion 持久化的载荷版本
     * @return 未注册或类型为空时返回空
     */
    public @Nullable AuditPayloadCodec<?> find(@Nullable String payloadType, int payloadVersion) {
        if (payloadType == null || payloadType.isBlank()) {
            return null;
        }
        return codecs.get(key(payloadType, payloadVersion));
    }

    /**
     * 按运行时载荷类型查找编码 Codec。
     *
     * @param payload 待编码的审计载荷
     * @return 没有精确匹配运行时类型的 Codec 时返回空
     */
    public @Nullable AuditPayloadCodec<?> find(@Nullable AuditPayload payload) {
        if (payload == null) {
            return null;
        }
        return payloadCodecs.get(payload.getClass());
    }

    /**
     * 组合持久化载荷类型与版本形成无歧义注册键。
     *
     * @param payloadType 已校验的固定载荷类型
     * @param payloadVersion 正整数载荷结构版本
     * @return 注册表内部使用的类型版本键
     */
    private @NotNull String key(@NotNull String payloadType, int payloadVersion) {
        return payloadType + ":" + payloadVersion;
    }
}
