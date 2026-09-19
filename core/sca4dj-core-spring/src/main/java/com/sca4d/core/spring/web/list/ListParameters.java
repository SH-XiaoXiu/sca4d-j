package com.sca4d.core.spring.web.list;

import com.sca4d.core.api.query.ListRequest;
import com.sca4d.core.api.query.PageRequest;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** 暂存 Controller 收到的分页和原始排序参数，直到列表声明完成安全字段转换。 */
public final class ListParameters {

    private final @NotNull PageRequest page;

    private final @NotNull List<@NotNull String> sort;

    private final int maxSortFields;

    /**
     * 创建只供 Core Spring 参数解析器使用的列表参数。
     *
     * @param page 已通过全局和参数级上限校验的分页要求
     * @param sort 按 HTTP 参数出现顺序保存的原始排序值
     * @param maxSortFields 当前配置允许的最大排序字段数量
     * @throws NullPointerException page、sort 或其中的值为空时抛出
     * @throws IllegalArgumentException maxSortFields 不是正数时抛出
     */
    public ListParameters(
            @NotNull PageRequest page,
            @NotNull List<@NotNull String> sort,
            int maxSortFields) {
        this.page = Objects.requireNonNull(page, "列表分页参数不能为空");
        this.sort = List.copyOf(Objects.requireNonNull(sort, "列表排序参数不能为空"));
        if (maxSortFields < 1) {
            throw new IllegalArgumentException("最大排序字段数量必须为正数");
        }
        this.maxSortFields = maxSortFields;
    }

    /**
     * 使用当前接口的声明校验并转换请求参数。
     *
     * @param definition 当前接口允许的字段、默认排序和页大小限制
     * @return 不再包含外部字符串的列表查询要求
     * @throws com.sca4d.core.api.error.BusinessException 任一列表参数无效时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public @NotNull ListRequest resolve(@NotNull ListDefinition definition) {
        return Objects.requireNonNull(definition, "列表声明不能为空")
                .resolve(page, sort, maxSortFields);
    }
}
