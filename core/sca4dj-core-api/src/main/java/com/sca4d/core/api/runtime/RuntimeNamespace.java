package com.sca4d.core.api.runtime;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 使用应用技术 ID 为共享基础设施资源建立运行时命名空间。 */
public final class RuntimeNamespace {

    private final String applicationId;

    /**
     * 创建应用运行时命名空间。
     *
     * @param applicationId 不包含空白和冒号的应用技术 ID
     * @throws IllegalArgumentException 应用技术 ID 包含空白或冒号时抛出
     */
    public RuntimeNamespace(String applicationId) {
        this.applicationId = requireSegment(applicationId, "应用技术 ID");
    }

    /**
     * 返回应用声明的技术 ID。
     *
     * @return 不包含空白和冒号的应用技术 ID
     */
    @Contract(pure = true)
    public @NotNull String applicationId() {
        return applicationId;
    }

    /**
     * 创建由当前应用和 Sca4d-J 模块共同限定的资源名称。
     *
     * @param segments 模块名以及模块内部资源名，每段都不能包含空白或冒号
     * @return 格式为 {@code <app-id>:sca4dj:<segment>...} 的资源名称
     * @throws IllegalArgumentException 没有名称段，或任一名称段包含空白、冒号时抛出
     */
    @Contract(pure = true)
    public @NotNull String sca4dj(String @NotNull... segments) {
        String[] checkedSegments = Objects.requireNonNull(segments, "资源名称段不能为空");
        if (checkedSegments.length == 0) {
            throw new IllegalArgumentException("资源名称至少需要一个名称段");
        }
        StringBuilder result = new StringBuilder(applicationId).append(":sca4dj");
        for (String segment : checkedSegments) {
            result.append(':').append(requireSegment(segment, "资源名称段"));
        }
        return result.toString();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof RuntimeNamespace namespace
                        && applicationId.equals(namespace.applicationId);
    }

    @Override
    public int hashCode() {
        return applicationId.hashCode();
    }

    @Override
    public String toString() {
        return applicationId;
    }

    /**
     * 校验一个命名空间名称段。
     *
     * @param value 待校验文本
     * @param name 异常中使用的字段名称
     * @return 清除首尾空白后的名称段
     */
    private static String requireSegment(String value, String name) {
        String checked = TextUtils.requireText(value, name);
        if (!checked.equals(value)
                || checked.indexOf(':') >= 0
                || checked.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(name + "不能包含空白或冒号");
        }
        return checked;
    }
}
