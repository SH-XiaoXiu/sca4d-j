package com.sca4d.core.api.authentication;

import com.sca4d.core.api.utils.TextUtils;

/** 保存任意认证 Session 的不透明标识。 */
public class SessionId {

    private final String value;

    /**
     * 创建 Session 标识。
     *
     * @param value 非空白标识值
     */
    public SessionId(String value) {
        this.value = TextUtils.requireText(value, "会话标识");
    }

    /**
     * 返回原始标识值。
     *
     * @return 原始标识值
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof SessionId sessionId && value.equals(sessionId.value);
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
