package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 保存跨应用实例互斥业务操作使用的锁键名称段。 */
public final class LockKey {

    private final List<String> segments;

    private final String value;

    /**
     * 创建互斥锁键。
     *
     * @param segments 模块、资源和对象标识等锁键名称段，每段都不能包含空白或冒号
     * @throws IllegalArgumentException 没有名称段，或任一名称段包含空白、冒号时抛出
     */
    public LockKey(String @NotNull... segments) {
        String[] checked = Objects.requireNonNull(segments, "锁键名称段不能为空");
        if (checked.length == 0) {
            throw new IllegalArgumentException("锁键至少需要一个名称段");
        }
        this.segments = Arrays.stream(checked).map(LockKey::requireSegment).toList();
        this.value = String.join(":", this.segments);
    }

    /**
     * 返回组成锁键的名称段。
     *
     * @return 不可修改的锁键名称段
     */
    public @NotNull @Unmodifiable List<@NotNull String> segments() {
        return List.copyOf(segments);
    }

    /**
     * 返回锁键值。
     *
     * @return 锁键值
     */
    public @NotNull String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof LockKey lockKey && value.equals(lockKey.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }

    /** @return 经过校验的锁键名称段 */
    private static @NotNull String requireSegment(@NotNull String value) {
        String checked = TextUtils.requireText(value, "锁键名称段");
        if (!checked.equals(value)
                || checked.indexOf(':') >= 0
                || checked.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("锁键名称段不能包含空白或冒号");
        }
        return checked;
    }
}
