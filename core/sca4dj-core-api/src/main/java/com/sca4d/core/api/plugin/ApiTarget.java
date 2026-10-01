package com.sca4d.core.api.plugin;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 标识服务或扩展及其可选实现编码，不包含反射类名。 */
public final class ApiTarget {
    private final @NotNull TreeCode code;

    private final @Nullable TreeCode key;

    /** 契约编码和实现编码均不允许通配符；服务或单实现契约的 key 为空。 */
    public ApiTarget(@NotNull TreeCode code, @Nullable TreeCode key) {
        this.code = Objects.requireNonNull(code);
        this.key = key;
        TreeCode wildcard = TreeCode.ofSegment("*");
        if (code.hasNode(wildcard) || key != null && key.hasNode(wildcard)) {
            throw new IllegalArgumentException("插件目标编码不能包含通配符");
        }
    }

    /** @return 契约编码 */
    public @NotNull TreeCode code() {
        return code;
    }

    /** @return 实现编码；单实现与服务没有此值 */
    public @Nullable TreeCode key() {
        return key;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ApiTarget target && code.equals(target.code) && Objects.equals(key, target.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, key);
    }

    @Override
    public String toString() {
        return code + (key == null ? "" : "[" + key + "]");
    }
}
