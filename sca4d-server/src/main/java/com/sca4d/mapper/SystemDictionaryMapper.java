package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.entity.SysDictionaryItemEntity;
import com.sca4d.entity.SysDictionaryTypeEntity;
import com.sca4d.system.DictionaryKind;
import com.sca4d.system.DictionaryOrder;
import com.sca4d.system.DictionaryStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 系统字典表的代码目录同步 SQL Mapper。 */
@Mapper
public interface SystemDictionaryMapper extends BaseMapper<SysDictionaryTypeEntity> {

    /**
     * 获取代码字典启动同步使用的 PostgreSQL 事务级互斥锁。
     *
     * <p>锁在当前事务提交或回滚时自动释放，用于防止多个应用节点同时为同一新枚举分配不同主键。
     */
    int lockCodeDictionaryCatalog();

    /**
     * 按固定类型编码获取可配置字典创建事务锁。
     *
     * @param code 待占用的字典类型编码
     * @return PostgreSQL 锁函数执行后的固定标记 1
     */
    int lockDictionaryTypeCode(@Param("code") @NotNull String code);

    /**
     * 按父字典和值获取可配置字典项创建事务锁。
     *
     * @param dictionaryId 父字典类型主键
     * @param value 待占用的字典项值
     * @return PostgreSQL 锁函数执行后的固定标记 1
     */
    int lockDictionaryItemValue(
            @Param("dictionaryId") @NotNull Long dictionaryId, @Param("value") @NotNull String value);

    /**
     * 获取字典类型集合或单个字典项集合的排序事务锁。
     *
     * @param scope 平台、租户或父字典对应的固定锁范围
     * @return PostgreSQL 锁函数执行后的固定标记 1
     */
    int lockDictionaryOrder(@Param("scope") @NotNull String scope);

    /**
     * 查询一个平台或租户范围内未删除字典类型的当前最大排序值。
     *
     * @param tenantId 精确所属租户；空表示平台全局
     * @return 当前最大排序值；范围内没有字典时返回 {@code null}
     */
    @Nullable
    Integer selectMaxDictionaryTypeSortOrder(@Param("tenantId") @Nullable Long tenantId);

    /**
     * 查询一个字典内未删除字典项的当前最大排序值。
     *
     * @param dictionaryId 父字典类型主键
     * @return 当前最大排序值；字典内没有项目时返回 {@code null}
     */
    @Nullable
    Integer selectMaxDictionaryItemSortOrder(@Param("dictionaryId") @NotNull Long dictionaryId);

    /**
     * 查询全部平台全局字典类型，包括需要按代码数据恢复的逻辑删除投影。
     *
     * @return 按编码固定排序的全局字典类型
     */
    @NotNull
    List<SysDictionaryTypeEntity> selectGlobalDictionaryTypes();

    /**
     * 查询一组字典类型的全部字典项，包括需要按代码数据恢复的逻辑删除投影。
     *
     * @param typeIds 已去重的字典类型主键；调用方不传空集合
     * @return 按类型、排序和主键固定排序的字典项
     */
    @NotNull
    List<SysDictionaryItemEntity> selectDictionaryItems(@Param("typeIds") @NotNull List<Long> typeIds);

    /**
     * 批量新增或刷新代码字典类型投影。
     *
     * <p>已有行只刷新代码提供的名称键、来源和启用状态，保留管理端维护的展示排序。
     *
     * @param types 已分配主键的完整代码字典类型投影
     * @return PostgreSQL 报告的写入行数
     */
    int upsertCodeDictionaryTypes(@Param("types") @NotNull List<SysDictionaryTypeEntity> types);

    /**
     * 批量新增或刷新代码字典项投影。
     *
     * <p>已有行只刷新代码提供的标签键、色调、可选择状态和启用状态，保留管理端维护的展示排序。
     *
     * @param items 已分配主键并绑定类型主键的完整代码字典项投影
     * @return PostgreSQL 报告的写入行数
     */
    int upsertCodeDictionaryItems(@Param("items") @NotNull List<SysDictionaryItemEntity> items);

    /**
     * 写入一个已经分配主键的可配置字典类型。
     *
     * <p>该 SQL 把序列化后的多语言标签显式转换为 PostgreSQL JSONB；普通 BaseMapper 插入不能绑定该数据库类型。
     *
     * @param type 已完成所属范围、编码和启用状态校验的字典类型
     * @return 实际写入行数，只能为 1
     */
    int insertConfigurableDictionaryType(@Param("type") @NotNull SysDictionaryTypeEntity type);

    /**
     * 写入一个已经分配主键的可配置字典项。
     *
     * <p>该 SQL 负责把序列化后的多语言标签显式转换为 PostgreSQL JSONB；父子关系和唯一性仍由事务内校验及数据库约束共同保证。
     *
     * @param item 已完成父字典、固定值和启用状态校验的字典项
     * @return 实际写入行数，只能为 1
     */
    int insertConfigurableDictionaryItem(@Param("item") @NotNull SysDictionaryItemEntity item);

    /**
     * 按精确管理范围锁定一个未删除字典类型。
     *
     * <p>父类型内容变更、删除和子项写入通过同一 PostgreSQL 行锁串行化；调用方必须位于事务中。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @return 已锁定的字典类型；范围不匹配或不存在时返回 {@code null}
     */
    @Nullable
    SysDictionaryTypeEntity selectDictionaryTypeForUpdate(
            @Param("tenantId") @Nullable Long tenantId,
            @Param("dictionaryId") @NotNull Long dictionaryId);

    /**
     * 原子更新可配置字典的多语言名称和状态。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param nameLabels 已序列化的多语言名称 JSON
     * @param status 新启用状态
     * @param version 当前乐观锁版本
     * @return 实际更新行数，只能为 0 或 1
     */
    int updateConfigurableDictionaryType(
            @Param("tenantId") @Nullable Long tenantId,
            @Param("dictionaryId") @NotNull Long dictionaryId,
            @Param("nameLabels") @NotNull String nameLabels,
            @Param("status") @NotNull DictionaryStatus status,
            @Param("version") long version);

    /**
     * 原子更新可配置字典项内容，并同时约束父字典范围和含义类型。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param itemId 字典项主键
     * @param kind 父字典含义类型
     * @param value 新业务编码或输入建议文本
     * @param labelLabels 已序列化的多语言标签 JSON
     * @param tone 新含义色调
     * @param selectable 是否允许用于新输入
     * @param status 新启用状态
     * @param version 当前乐观锁版本
     * @return 实际更新行数，只能为 0 或 1
     */
    int updateConfigurableDictionaryItem(
            @Param("tenantId") @Nullable Long tenantId,
            @Param("dictionaryId") @NotNull Long dictionaryId,
            @Param("itemId") @NotNull Long itemId,
            @Param("kind") @NotNull DictionaryKind kind,
            @Param("value") @NotNull String value,
            @Param("labelLabels") @NotNull String labelLabels,
            @Param("tone") @NotNull DictionaryTone tone,
            @Param("selectable") boolean selectable,
            @Param("status") @NotNull DictionaryStatus status,
            @Param("version") long version);

    /**
     * 将未删除字典项批量推进到逻辑删除终态。
     *
     * @param dictionaryId 父字典类型主键
     * @return 实际更新的字典项数量
     */
    int markDictionaryItemsDeleted(@Param("dictionaryId") @NotNull Long dictionaryId);

    /**
     * 物理删除输入建议字典的全部字典项。
     *
     * @param dictionaryId 父字典类型主键
     * @return 实际删除的字典项数量
     */
    int deleteDictionaryItems(@Param("dictionaryId") @NotNull Long dictionaryId);

    /**
     * 在所属范围、含义类型和版本匹配时把字典类型推进到逻辑删除终态。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param kind 当前字典含义类型
     * @param version 当前乐观锁版本
     * @return 实际更新行数，只能为 0 或 1
     */
    int markDictionaryTypeDeleted(
            @Param("tenantId") @Nullable Long tenantId,
            @Param("dictionaryId") @NotNull Long dictionaryId,
            @Param("kind") @NotNull DictionaryKind kind,
            @Param("version") long version);

    /**
     * 在父字典范围、含义类型和版本匹配时把业务编码项推进到逻辑删除终态。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param itemId 字典项主键
     * @param version 当前乐观锁版本
     * @return 实际更新行数，只能为 0 或 1
     */
    int markBusinessDictionaryItemDeleted(
            @Param("tenantId") @Nullable Long tenantId,
            @Param("dictionaryId") @NotNull Long dictionaryId,
            @Param("itemId") @NotNull Long itemId,
            @Param("version") long version);

    /**
     * 在父字典范围、含义类型和版本匹配时物理删除输入建议项。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param itemId 字典项主键
     * @param version 当前乐观锁版本
     * @return 实际删除行数，只能为 0 或 1
     */
    int deleteInputSuggestionItem(
            @Param("tenantId") @Nullable Long tenantId,
            @Param("dictionaryId") @NotNull Long dictionaryId,
            @Param("itemId") @NotNull Long itemId,
            @Param("version") long version);

    /**
     * 将指定租户的全部未删除字典项同步推进到不可选的删除终态。
     *
     * @param tenantId 已进入删除终态的租户主键
     * @return 实际隔离字典项数量
     */
    int isolateTenantDictionaryItems(@Param("tenantId") @NotNull Long tenantId);

    /**
     * 将指定租户的全部未删除字典类型同步推进到删除终态。
     *
     * @param tenantId 已进入删除终态的租户主键
     * @return 实际隔离字典类型数量
     */
    int isolateTenantDictionaryTypes(@Param("tenantId") @NotNull Long tenantId);

    /** @param tenantId 已进入删除终态的租户主键 */
    void deleteIsolatedTenantDictionaries(@Param("tenantId") @NotNull Long tenantId);

    /**
     * 使用各类型原版本一次性写入当前范围的完整展示顺序。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param orders 已按最终位置分配排序值的完整类型集合
     * @return 实际更新行数；少于集合大小表示至少一个类型已经变化
     */
    int reorderDictionaryTypes(
            @Param("tenantId") @Nullable Long tenantId,
            @Param("orders") @NotNull List<DictionaryOrder> orders);

    /**
     * 使用各字典项原版本一次性写入一个字典的完整展示顺序。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param orders 已按最终位置分配排序值的完整字典项集合
     * @return 实际更新行数；少于集合大小表示至少一个字典项已经变化
     */
    int reorderDictionaryItems(
            @Param("tenantId") @Nullable Long tenantId,
            @Param("dictionaryId") @NotNull Long dictionaryId,
            @Param("orders") @NotNull List<DictionaryOrder> orders);
}
