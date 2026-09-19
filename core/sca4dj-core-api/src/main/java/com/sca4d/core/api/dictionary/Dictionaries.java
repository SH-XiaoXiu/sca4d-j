package com.sca4d.core.api.dictionary;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Set;

/** 查询 Java 枚举与数据库选项聚合后的字典。 */
public interface Dictionaries {

    /**
     * 返回当前应用声明或数据库创建的全部字典键。
     *
     * @return 按键排序的不可修改集合
     */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    Set<@NotNull String> keys();

    /**
     * 返回代码字典是否明确允许数据库增加选项。
     *
     * @param key 字典键
     * @return 可扩展代码字典返回 {@code true}；动态字典和封闭代码字典返回 {@code false}
     * @throws IllegalArgumentException 字典不存在时抛出
     */
    @CheckReturnValue
    boolean allowsDatabaseOptions(@NotNull String key);

    /**
     * 查询一个字典的代码选项与数据库选项。
     *
     * @param key 字典键
     * @return 先按排序值、再按选项值排序的不可修改选项
     * @throws IllegalArgumentException 字典不存在时抛出
     */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    List<@NotNull DictionaryOption> options(@NotNull String key);
}
