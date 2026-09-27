package com.sca4d.core.api.authentication.principalsession;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

/**
 * 保存一种 Resource 类型在 Session 中受信任的正数标识集合。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record，保留普通不可变类型。
public class ResourceIdSet {

    private final @NotNull TreeCode resourceType;

    private final @NotNull LongIdSet ids;

    /**
     * 创建一种 Resource 类型的非空可信标识集合。
     *
     * @param resourceType 与 {@link Resource#type()} 返回值一致的类型
     * @param ids          能够由规范正十进制文本表示的 Resource 标识
     * @throws NullPointerException     类型或标识集合为 {@code null} 时抛出
     * @throws IllegalArgumentException 类型为空白，或标识集合为空、包含非正数或重复值时抛出
     */
    public ResourceIdSet(
            @NotNull TreeCode resourceType,
            @NotNull Collection<@NotNull Long> ids) {
        this.resourceType = Objects.requireNonNull(resourceType, "Resource 类型不能为空");
        this.ids = new LongIdSet(ids);
        if (this.ids.isEmpty()) {
            throw new IllegalArgumentException("Resource 标识集合不能为空");
        }
    }

    /**
     * 返回当前集合对应的 Resource 类型。
     *
     * @return Resource 类型
     */
    @Contract(pure = true)
    public @NotNull TreeCode resourceType() {
        return resourceType;
    }

    /**
     * 返回建立 Session 时确认的数字 Resource 标识。
     *
     * @return 不可变标识集合
     */
    @Contract(pure = true)
    public @NotNull LongIdSet ids() {
        return ids;
    }
}
