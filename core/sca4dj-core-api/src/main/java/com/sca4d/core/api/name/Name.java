package com.sca4d.core.api.name;

import com.sca4d.core.api.isolation.AppIsolation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 包装一个不可变名称字符串，提供名称比较和显式 App 隔离，不解释节点或动态值。 */
public class Name implements AppIsolation<Name> {

    private final @NotNull String value;

    /** 保存名称原文；拒绝空白名称，大小写和字符规则由具体名称类型决定。 */
    public Name(@NotNull String value) {
        this.value = Objects.requireNonNull(value, "名称不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException("名称不能为空白");
        }
    }

    /** 复制已经校验的名称字符串，供专属名称类型复用。 */
    protected Name(@NotNull Name name) {
        value = Objects.requireNonNull(name).value;
    }

    /** 在序列化边界输出名称原文；具有结构的子类负责选择相应格式。 */
    public @NotNull String format(@NotNull NameFormat format) {
        return logicalFormat(Objects.requireNonNull(format));
    }

    /** 输出尚未应用隔离的逻辑名称；普通 Name 不解释分隔符。 */
    protected @NotNull String logicalFormat(@NotNull NameFormat format) {
        return value;
    }

    /** 使用冒号格式应用 App 隔离，返回新的名称值。 */
    @Override
    public final @NotNull Name isolate() {
        return isolate(NameFormat.COLON);
    }

    /** 在指定输出边界应用 App 前缀；隔离依据不进入 Code 的稳定节点。 */
    public @NotNull Name isolate(@NotNull NameFormat format) {
        Objects.requireNonNull(format);
        return new IsolatedName(appName().format(format) + format.separator() + logicalFormat(format));
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof Name name && getClass() == name.getClass() && value.equals(name.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), value);
    }

    /**
     * 名称原文供日志定位；实际协议边界显式使用 format。
     *
     * @return 名称原文供日志定位；实际协议边界显式使用 format
     */
    @Override
    public @NotNull String toString() {
        return value;
    }

    /** 保存已经应用隔离的名称字符串，重复隔离直接复用此不可变值。 */
    private static class IsolatedName extends Name {

        private IsolatedName(@NotNull String value) {
            super(value);
        }

        @Override
        public @NotNull Name isolate(@NotNull NameFormat format) {
            Objects.requireNonNull(format);
            return this;
        }
    }
}
