package com.sca4d.system;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 汇总各业务模块注册的字典值引用检查。 */
@Component
public class DictionaryReferenceRegistry {

    private final List<DictionaryValueReference> references;

    /**
     * 建立固定的业务字典引用检查顺序。
     *
     * <p>同一字典可以被多个业务模块引用，因此不限制编码唯一注册。每个匹配实现最多执行一次集合查询，总调用次数等于声明支持该字典的实现数，
     * 不随待检查值数量增长。
     *
     * @param references 当前应用注册的业务模块引用实现
     */
    public DictionaryReferenceRegistry(@NotNull Collection<DictionaryValueReference> references) {
        this.references = references.stream()
                .map(reference -> Objects.requireNonNull(reference, "字典引用实现不能为空"))
                .toList();
    }

    /**
     * 查找一组业务编码中仍被任意业务模块引用的值。
     *
     * @param tenantId 字典所属租户；空表示平台全局字典
     * @param dictionaryCode 字典类型固定编码
     * @param values 待检查的去重业务编码集合
     * @return 保持输入遍历顺序的被引用值集合
     */
    public @NotNull Set<String> referencedValues(
            @Nullable Long tenantId, @NotNull String dictionaryCode, @NotNull Set<String> values) {
        if (values.isEmpty()) {
            return Set.of();
        }
        Set<String> referencedValues = new LinkedHashSet<>();
        for (DictionaryValueReference reference : references) {
            if (!reference.supports(dictionaryCode)) {
                continue;
            }
            Set<String> found = reference.findReferencedValues(tenantId, dictionaryCode, Set.copyOf(values));
            if (found == null || !values.containsAll(found)) {
                throw new IllegalStateException("字典引用实现返回了候选集合之外的值: " + reference.getClass().getName());
            }
            referencedValues.addAll(found);
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (String value : values) {
            if (referencedValues.contains(value)) {
                ordered.add(value);
            }
        }
        return Collections.unmodifiableSet(ordered);
    }
}
