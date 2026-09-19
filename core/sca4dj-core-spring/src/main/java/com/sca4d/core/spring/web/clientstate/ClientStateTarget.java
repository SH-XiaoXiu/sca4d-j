package com.sca4d.core.spring.web.clientstate;

import com.sca4d.core.api.utils.TextUtils;

/** 当前 HTTP 调用完成后需要客户端重新读取的状态目标。 */
public final class ClientStateTarget {

    private final String value;

    private ClientStateTarget(String value) {
        String checked = TextUtils.requireText(value, "客户端状态目标");
        if (checked.length() > 128 || checked.indexOf(',') >= 0 || checked.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("客户端状态目标格式错误");
        }
        this.value = checked;
    }

    /**
     * 创建业务模块声明的固定状态目标。
     *
     * @param value 业务模块声明的状态编码
     * @return 已校验的状态目标
     */
    public static ClientStateTarget of(String value) {
        return new ClientStateTarget(value);
    }

    /**
     * 创建指向单个业务资源的状态目标。
     *
     * @param resourceType 资源类型编码
     * @param resourceId 正整数资源标识
     * @return 资源级刷新目标
     */
    public static ClientStateTarget resource(String resourceType, long resourceId) {
        if (!resourceType.matches("[a-z][a-z0-9-]*") || resourceId < 1) {
            throw new IllegalArgumentException("客户端资源状态目标格式错误");
        }
        return new ClientStateTarget(resourceType + ":" + resourceId);
    }

    /**
     * 返回写入响应头的目标编码。
     *
     * @return 写入响应头的状态编码
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ClientStateTarget target && value.equals(target.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
