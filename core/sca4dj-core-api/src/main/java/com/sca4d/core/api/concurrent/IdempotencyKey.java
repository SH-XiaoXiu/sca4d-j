package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 一次幂等操作的不透明键。 */
public class IdempotencyKey {

    /** Call 入口提供的原始幂等键输入；缺失时由幂等阶段报告调用方未提供键。 */
    public static final DataKey<String> INPUT = DataKey.of("idempotencyKey", String.class);

    private final String value;

    /**
     * 创建幂等键。
     *
     * @param value 非空白幂等键
     */
    public IdempotencyKey(String value) {
        this.value = TextUtils.requireText(value, "幂等键");
    }

    /** 返回已经规范化的幂等键。 */
    public static @NotNull IdempotencyKey fromValue(@NotNull IdempotencyKey value) {
        return Objects.requireNonNull(value, "幂等键不能为空");
    }

    /** 把文本输入规范化为幂等键。 */
    public static @NotNull IdempotencyKey fromValue(@NotNull CharSequence value) {
        String checked = TextUtils.requireText(
                Objects.requireNonNull(value, "幂等键不能为空").toString(),
                "客户端幂等键",
                128);
        if (checked.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("客户端幂等键不能包含控制字符");
        }
        return new IdempotencyKey(checked);
    }

    /** 从动态输入归一化幂等键；只接受已经是键对象或文本值。 */
    public static @NotNull IdempotencyKey fromValue(@NotNull Object value) {
        Objects.requireNonNull(value, "幂等键不能为空");
        if (value instanceof IdempotencyKey key) {
            return key;
        }
        if (value instanceof CharSequence text) {
            return fromValue(text);
        }
        throw new IllegalArgumentException("幂等键必须是 IdempotencyKey 或文本值");
    }

    /**
     * 幂等键值。
     *
     * @return 幂等键值
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof IdempotencyKey idempotencyKey
                        && value.equals(idempotencyKey.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
