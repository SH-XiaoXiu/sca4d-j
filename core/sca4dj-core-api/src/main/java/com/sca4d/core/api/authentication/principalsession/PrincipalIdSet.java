package com.sca4d.core.api.authentication.principalsession;

import com.sca4d.core.api.identity.Principal;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Objects;

/**
 * 保存一种精确 Principal Java 类型在 Session 中受信任的标识集合。
 *
 * @param <P> 当前集合对应的 Principal 类型
 */
@SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record，保留普通不可变类型。
public class PrincipalIdSet<P extends Principal> {

    private final @NotNull Class<P> principalType;

    private final @NotNull LongIdSet ids;

    /**
     * 创建一种 Principal 类型的非空可信标识集合。
     *
     * @param principalType 请求期候选 Principal 必须精确匹配的 Java 类型
     * @param ids 建立 Session 时确认存在且有效的 Principal 标识
     * @throws NullPointerException 类型或标识集合为 {@code null} 时抛出
     * @throws IllegalArgumentException Principal 类型不是具体类，或标识集合为空、包含非正数或重复值时抛出
     */
    public PrincipalIdSet(
            @NotNull Class<P> principalType,
            @NotNull Collection<@NotNull Long> ids) {
        this.principalType = Objects.requireNonNull(principalType, "Principal 类型不能为空");
        if (this.principalType.isInterface() || Modifier.isAbstract(this.principalType.getModifiers())) {
            throw new IllegalArgumentException("Principal 类型必须是具体类: " + this.principalType.getName());
        }
        this.ids = new LongIdSet(ids);
        if (this.ids.isEmpty()) {
            throw new IllegalArgumentException("Principal 标识集合不能为空");
        }
    }

    /**
     * 返回候选 Principal 必须精确匹配的 Java 类型。
     *
     * @return Principal Java 类型
     */
    @Contract(pure = true)
    public @NotNull Class<P> principalType() {
        return principalType;
    }

    /**
     * 返回建立 Session 时确认的标识集合。
     *
     * @return 不可变标识集合
     */
    @Contract(pure = true)
    public @NotNull LongIdSet ids() {
        return ids;
    }
}
