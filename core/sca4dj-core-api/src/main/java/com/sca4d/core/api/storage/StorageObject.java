package com.sca4d.core.api.storage;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 使用容器名称和对象键定位存储中的一个对象。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class StorageObject {
    private final @NotNull String container;

    private final @NotNull String objectKey;

    /**
     * 创建存储对象位置。
     *
     * @param container Bucket 或本地命名空间
     * @param objectKey 由文件模块生成的对象键
     * @throws IllegalArgumentException 任一值为空白或超过允许长度时抛出
     */
    public StorageObject(@NotNull String container, @NotNull String objectKey) {
        this.container = required(container, 128, "存储容器");
        this.objectKey = required(objectKey, 512, "对象键");
    }

    /**
     * 返回对象所在容器。
     *
     * @return Bucket 或本地命名空间
     */
    public @NotNull String container() {
        return container;
    }

    /**
     * 返回容器内的对象键。
     *
     * @return 由文件模块生成的对象键
     */
    public @NotNull String objectKey() {
        return objectKey;
    }

    private static @NotNull String required(@NotNull String value, int maxLength, @NotNull String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空").trim();
        if (checked.isEmpty() || checked.length() > maxLength)
            throw new IllegalArgumentException(name + "长度必须在 1 到 " + maxLength + " 之间");
        return checked;
    }
}
