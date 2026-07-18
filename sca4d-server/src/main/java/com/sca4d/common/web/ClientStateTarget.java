package com.sca4d.common.web;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 当前请求响应中提示前端重新读取的状态目标。 */
public final class ClientStateTarget {

    private static final int MAX_LENGTH = 128;

    private final String value;

    private ClientStateTarget(@NotNull String value) {
        if (value.isBlank()
                || !value.equals(value.trim())
                || value.length() > MAX_LENGTH
                || value.indexOf(',') >= 0
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("前端状态刷新目标格式错误");
        }
        this.value = value;
    }

    /**
     * 创建由业务模块声明的固定状态目标。
     *
     * @param value 写入 X-Client-State-Refresh 的固定编码
     * @return 已校验的状态目标
     */
    public static @NotNull ClientStateTarget of(@NotNull String value) {
        return new ClientStateTarget(value);
    }

    /**
     * 创建指向一个业务资源的动态状态目标。
     *
     * @param resourceType 小写字母、数字和连字符组成的资源类型
     * @param resourceId 资源主键
     * @return 格式为 resourceType:resourceId 的状态目标
     */
    public static @NotNull ClientStateTarget resource(
            @NotNull String resourceType, @NotNull Long resourceId) {
        if (!resourceType.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("前端状态刷新资源类型格式错误");
        }
        return new ClientStateTarget(resourceType + ":" + resourceId);
    }

    /** @return 写入 X-Client-State-Refresh 的状态目标编码 */
    public @NotNull String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ClientStateTarget that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
