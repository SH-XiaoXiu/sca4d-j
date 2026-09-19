package com.sca4d.core.spring.validation;

import com.sca4d.core.api.region.PhoneNumberCondition;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 保存应用显式声明的 PhoneNumberCondition Bean，并拒绝重复实现类型。 */
public final class PhoneNumberConditionRegistry {

    private final List<PhoneNumberCondition> conditions;

    /**
     * 创建条件目录。
     *
     * @param conditions Spring 容器中的显式条件 Bean
     */
    public PhoneNumberConditionRegistry(@NotNull List<PhoneNumberCondition> conditions) {
        Objects.requireNonNull(conditions, "电话号码条件 Bean 不能为空");
        Map<Class<?>, PhoneNumberCondition> types = new LinkedHashMap<>();
        for (PhoneNumberCondition condition : conditions) {
            PhoneNumberCondition checked = Objects.requireNonNull(condition, "电话号码条件 Bean 不能包含 null");
            Class<?> type = ClassUtils.getUserClass(checked);
            if (types.putIfAbsent(type, checked) != null) {
                throw new IllegalStateException("PhoneNumberCondition Bean 类型重复: " + type.getName());
            }
        }
        this.conditions = List.copyOf(conditions);
    }

    /**
     * 按注解引用的类型查找唯一条件 Bean。
     *
     * @param type 注解声明的条件类型
     * @return 唯一匹配 Bean
     * @throws IllegalStateException 没有 Bean 或存在多个可赋值 Bean 时抛出
     */
    public @NotNull PhoneNumberCondition resolve(@NotNull Class<? extends PhoneNumberCondition> type) {
        Objects.requireNonNull(type, "电话号码条件类型不能为空");
        List<PhoneNumberCondition> matched = conditions.stream().filter(type::isInstance).toList();
        if (matched.size() != 1) {
            throw new IllegalStateException(
                    "PhoneNumberCondition Bean 数量必须为 1: type=" + type.getName() + " count=" + matched.size());
        }
        return matched.getFirst();
    }
}
