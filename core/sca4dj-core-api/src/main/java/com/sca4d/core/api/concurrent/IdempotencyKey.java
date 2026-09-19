package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.utils.TextUtils;

/** 一次幂等操作的不透明键。 */
public final class IdempotencyKey {

    private final String value;

    /**
     * 创建幂等键。
     *
     * @param value 非空白幂等键
     */
    public IdempotencyKey(String value) {
        this.value = TextUtils.requireText(value, "幂等键");
    }

    /**
     * 返回幂等键值。
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
