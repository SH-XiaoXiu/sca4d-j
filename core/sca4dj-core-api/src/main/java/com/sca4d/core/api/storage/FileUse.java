package com.sca4d.core.api.storage;

import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 标识文件准备绑定或已经绑定的业务对象。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class FileUse {
    private final @NotNull String scene;

    private final @NotNull Resource target;

    /**
     * 创建文件申请时声明的绑定用途。
     *
     * @param scene  App 定义的文件业务场景编码
     * @param target 文件准备绑定的目标业务资源
     * @throws IllegalArgumentException scene、目标类型或目标标识无效时抛出
     */
    public FileUse(@NotNull String scene, @NotNull Resource target) {
        this.scene = required(scene, 64, "文件场景");
        Resource checked = Objects.requireNonNull(target, "文件目标资源不能为空");
        if (checked.type().length() > 128) {
            throw new IllegalArgumentException("目标资源类型长度必须在 1 到 128 之间");
        }
        required(checked.id(), 128, "目标资源标识");
        this.target = checked;
    }

    /**
     * 返回 App 定义的文件业务场景。
     *
     * @return 业务场景编码
     */
    public @NotNull String scene() {
        return scene;
    }

    /**
     * 返回文件准备绑定的目标业务资源。
     *
     * @return 目标业务资源
     */
    public @NotNull Resource target() {
        return target;
    }

    /**
     * 校验并返回去除首尾空白后的场景或资源文本。
     */
    private static @NotNull String required(@NotNull String value, int maxLength, @NotNull String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空").trim();
        if (checked.isEmpty()
                || checked.length() > maxLength
                || checked.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(name + "长度必须在 1 到 " + maxLength + " 之间");
        return checked;
    }
}
