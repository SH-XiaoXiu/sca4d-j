package com.sca4d.core.api.resource;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.Permission;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 表示权限判断所保护的业务资源（Resource）。
 *
 * <p>资源由类型和标识共同确定。接口、任务等固定入口也属于资源，参见
 * {@link Operation}。
 *
 * @see Permission
 */
public interface Resource {

    /**
     * 返回资源类型。
     *
     * @return 非空白资源类型
     */
    @NotNull
    TreeCode type();

    /**
     * 返回资源标识。
     *
     * @return 非空白业务标识
     */
    @NotNull
    String id();

    /** 保存 Resource 的稳定类型和标识；它是 Resource 的最小实现。 */
    final class Ref implements Resource {
        private final @NotNull String id;

        private final @NotNull TreeCode type;

        /**
         * 使用文本形式的资源类型创建引用。
         *
         * @param type 资源类型编码，按 {@link TreeCode#of(String)} 的规则校验
         * @param id 非空白业务标识
         * @throws NullPointerException 类型或标识为空时抛出
         * @throws IllegalArgumentException 类型编码不合法或标识为空白时抛出
         */
        public Ref(@NotNull String type, @NotNull String id) {
            this(TreeCode.of(type), id);
        }

        /**
         * 使用已校验的资源类型创建引用。
         *
         * @param type 资源类型
         * @param id 非空白业务标识
         * @throws NullPointerException 类型或标识为空时抛出
         * @throws IllegalArgumentException 标识为空白时抛出
         */
        public Ref(@NotNull TreeCode type, @NotNull String id) {
            this.type = Objects.requireNonNull(type, "资源类型不能为空");
            this.id = Objects.requireNonNull(id, "资源标识不能为空");
            if (id.isBlank()) {
                throw new IllegalArgumentException("资源标识不能为空白");
            }
        }

        /**
         * 复制任意 Resource 的类型和标识，返回不依赖原对象的引用。
         *
         * @param resource 需要复制的资源
         * @return 与资源类型和标识相同的引用
         * @throws NullPointerException 资源为空时抛出
         */
        public static @NotNull Ref of(@NotNull Resource resource) {
            Resource checked = Objects.requireNonNull(resource, "资源不能为空");
            return new Ref(checked.type(), checked.id());
        }

        @Override
        public @NotNull TreeCode type() {
            return type;
        }

        @Override
        public @NotNull String id() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Ref reference
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
}
