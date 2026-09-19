package com.sca4d.core.api.authentication;

import com.sca4d.core.api.utils.TextUtils;

import java.util.Objects;

/** 保存认证方式编码。 */
@SuppressWarnings("ClassCanBeRecord")
public final class AuthenticationMethodCode {

    private final String value;

    /**
     * 创建认证方式编码。
     *
     * @param value 非空白编码值
     */
    public AuthenticationMethodCode(String value) {
        this.value = TextUtils.requireText(value, "认证方式编码");
    }

    /**
     * 读取构造时校验并保存的认证方式编码。
     *
     * @return 原始认证方式编码
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other == this || other instanceof AuthenticationMethodCode code && value.equals(code.value);
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
