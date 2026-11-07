package com.sca4d.core.api.identity;

import java.util.regex.Pattern;

/**
 * 保存账号所属认证域的键。
 *
 * <p>键值只能由大写字母、数字和下划线组成，以大写字母开头，长度不超过 128，与 {@code realm_key} 列一致。
 * 不符合规则时抛出 {@link IllegalArgumentException}，不做大小写转换或空白清理。
 */
public class RealmKey {

    private static final int MAXIMUM_LENGTH = 128;

    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_]*");

    private final String value;

    /**
     * 创建认证域键。
     *
     * @param value 以大写字母开头，仅含大写字母、数字和下划线，长度不超过 128 的键值
     * @throws IllegalArgumentException 键值为空、过长或不符合大写格式时抛出
     */
    public RealmKey(String value) {
        if (value == null || value.length() > MAXIMUM_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "认证域标识必须以大写字母开头，只能包含大写字母、数字和下划线，长度不超过 "
                            + MAXIMUM_LENGTH
                            + "：" + value);
        }
        this.value = value;
    }

    /**
     * 认证域键值。
     *
     * @return 认证域键值
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
