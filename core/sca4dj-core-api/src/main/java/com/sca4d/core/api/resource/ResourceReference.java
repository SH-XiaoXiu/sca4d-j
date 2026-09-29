package com.sca4d.core.api.resource;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 保存能够从数据库或消息中恢复的资源类型和资源标识。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class ResourceReference implements Resource {

    /** 把表达式返回的资源、正整数或文本标识转换为资源引用。 */
    public static @NotNull Resource fromValue(@Nullable Object value, @NotNull String resourceType) {
        if (value instanceof Resource resource) {
            if (!resourceType.isBlank() && !TreeCode.of(resourceType).equals(resource.type())) {
                throw new IllegalStateException("资源值与声明的 resourceType 不一致");
            }
            return resource;
        }
        if (resourceType.isBlank()) {
            throw new IllegalStateException("资源标识必须声明 resourceType");
        }
        String id;
        if (value instanceof Number number) {
            try {
                BigInteger integer = new BigDecimal(number.toString()).toBigIntegerExact();
                if (integer.signum() <= 0) {
                    throw new IllegalStateException("资源标识必须是正整数");
                }
                id = integer.toString();
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalStateException("资源标识必须是正整数", exception);
            }
        } else if (value instanceof String text && !text.isBlank()) {
            id = text.trim();
        } else {
            throw new IllegalStateException("资源值必须是 Resource、正整数或非空文本标识");
        }
        return new ResourceReference(resourceType, id);
    }
    private final @NotNull TreeCode type;

    private final @NotNull String id;

    /**
     * 创建资源引用。
     *
     * @param type 资源所属的稳定类型
     * @param id   当前资源类型内唯一的稳定标识
     * @throws NullPointerException     type 或 id 为空时抛出
     * @throws IllegalArgumentException type 或 id 为空白文本时抛出
     */
    public ResourceReference(@NotNull String type, @NotNull String id) {
        this(TreeCode.of(type), id);
    }

    /**
     * 创建使用已经规范化资源类型的资源引用。
     */
    public ResourceReference(@NotNull TreeCode type, @NotNull String id) {
        this.type = Objects.requireNonNull(type, "资源类型不能为空");
        this.id = TextUtils.requireText(id, "资源标识");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull TreeCode type() {
        return type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ResourceReference reference
                        && type.equals(reference.type)
                        && id.equals(reference.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }

    @Override
    public @NotNull String toString() {
        return type + ":" + id;
    }
}
