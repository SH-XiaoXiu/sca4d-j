package com.sca4d.system;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 业务模块声明业务编码字典值当前引用数据的扩展点。
 *
 * <p>实现由实际保存字典值的业务模块提供，只查询该模块自己的数据表和关系；不得接收动态表名、列名或 SQL。一次调用应使用集合查询返回命中的值，
 * 禁止按候选值逐项访问数据库。
 */
public interface DictionaryValueReference {

    /**
     * 判断当前引用实现是否拥有指定字典类型的关系数据。
     *
     * @param dictionaryCode 字典类型固定编码
     * @return 当前业务模块保存该字典值时返回 {@code true}
     */
    boolean supports(@NotNull String dictionaryCode);

    /**
     * 批量查找当前业务模块仍在引用的业务编码值。
     *
     * @param tenantId 字典所属租户；空表示平台全局字典
     * @param dictionaryCode 字典类型固定编码
     * @param values 待检查的去重业务编码集合
     * @return values 中仍被当前业务模块有效数据引用的值
     */
    @NotNull
    Set<String> findReferencedValues(
            @Nullable Long tenantId, @NotNull String dictionaryCode, @NotNull Set<String> values);
}
