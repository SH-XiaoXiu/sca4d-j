package com.sca4d.core.api.authentication;

import com.sca4d.core.api.utils.TextUtils;

import java.util.Objects;

/** 标识需要评估认证要求的应用动作。 */
@SuppressWarnings("ClassCanBeRecord")
public final class AuthenticationActionCode {

    private final String value;

    /**
     * 创建用于查询策略并约束完成结果的动作编码。
     *
     * @param value 去除首尾空白后仍非空的操作编码
     * @throws IllegalArgumentException 编码为空白时抛出
     */
    public AuthenticationActionCode(String value) {
        this.value = TextUtils.requireText(value, "认证动作编码");
    }

    /**
     * 读取认证尝试和完成结果绑定的动作。
     *
     * @return 构造时保存的操作编码
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AuthenticationActionCode code && value.equals(code.value);
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
