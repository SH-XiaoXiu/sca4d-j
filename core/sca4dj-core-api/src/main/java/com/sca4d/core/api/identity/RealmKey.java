package com.sca4d.core.api.identity;

import com.sca4d.core.api.utils.TextUtils;

/** 保存账号所属认证域的键。 */
public class RealmKey {

    private final String value;

    /**
     * 创建认证域键。
     *
     * @param value 非空白键值
     */
    public RealmKey(String value) {
        this.value = TextUtils.requireText(value, "认证域标识");
    }

    /**
     * 清除首尾空白后的键值。
     *
     * @return 清除首尾空白后的键值
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RealmKey realmKey && value.equals(realmKey.value);
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
