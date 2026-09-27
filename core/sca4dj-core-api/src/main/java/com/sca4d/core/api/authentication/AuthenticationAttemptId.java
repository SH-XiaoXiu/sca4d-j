package com.sca4d.core.api.authentication;

import com.sca4d.core.api.utils.TextUtils;

import java.util.Objects;

/** 跨请求认证尝试的不透明标识。 */
@SuppressWarnings("ClassCanBeRecord")
public class AuthenticationAttemptId {

    private final String value;

    /**
     * 创建调用方只能保存和回传的认证尝试标识。
     *
     * @param value 去除首尾空白后仍非空的不透明标识
     * @throws IllegalArgumentException 标识为空白时抛出
     */
    public AuthenticationAttemptId(String value) {
        this.value = TextUtils.requireText(value, "认证尝试标识");
    }

    /**
     * 读取跨请求定位认证尝试所需的值。
     *
     * @return 构造时保存的不透明标识
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AuthenticationAttemptId id && value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
