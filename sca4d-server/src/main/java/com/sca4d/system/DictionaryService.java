package com.sca4d.system;

import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.common.web.PageData;
import com.sca4d.system.dictionary.DictionaryOrderItem;
import com.sca4d.system.dictionary.DictionarySelection;
import com.sca4d.system.dictionary.ManagedDictionary;
import com.sca4d.system.dictionary.ManagedDictionaryItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** 按平台或租户可见范围查询本地化字典项，并维护可配置字典。 */
public interface DictionaryService {

    /**
     * 批量读取当前范围可见的有效字典及全部历史可解释选项。
     *
     * @param tenantId 租户范围；为空时只读取平台全局字典
     * @param codes 需要读取的固定字典类型编码，最多 50 个
     * @return 按数据库展示顺序排列并按当前请求语言解析的字典
     */
    @NotNull
    List<DictionarySelection> selections(@Nullable Long tenantId, @NotNull List<String> codes);

    /**
     * 分页读取精确平台或租户管理范围中的字典类型。
     *
     * @param tenantId 精确所属租户；空表示平台全局
     * @param search 固定类型编码关键词；空表示不筛选
     * @param kind 字典业务含义类型；空表示不筛选
     * @param status 启用状态；空表示不筛选
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 包含 ID、可编辑排序和版本的本地化字典分页
     */
    @NotNull
    PageData<ManagedDictionary> pageManagedTypes(
            @Nullable Long tenantId,
            @Nullable String search,
            @Nullable DictionaryKind kind,
            @Nullable DictionaryStatus status,
            long page,
            long size);

    /**
     * 读取精确管理范围中的一个字典类型。
     *
     * @param tenantId 精确所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @return 包含 ID、可编辑排序和版本的本地化字典
     */
    @NotNull
    ManagedDictionary requireManagedType(@Nullable Long tenantId, @NotNull Long dictionaryId);

    /**
     * 分页读取精确管理范围中的一个字典类型的字典项。
     *
     * @param tenantId 精确所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param search 固定值或建议文本关键词；空表示不筛选
     * @param status 启用状态；空表示不筛选
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 包含 ID、可编辑排序和版本的本地化字典项分页
     */
    @NotNull
    PageData<ManagedDictionaryItem> pageManagedItems(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @Nullable String search,
            @Nullable DictionaryStatus status,
            long page,
            long size);

    /**
     * 读取当前平台或租户管理范围中的完整字典类型顺序。
     *
     * @param tenantId 精确所属租户；空表示平台全局
     * @return 按当前展示顺序排列的轻量字典类型和版本
     */
    @NotNull
    List<DictionaryOrderItem> typeOrder(@Nullable Long tenantId);

    /**
     * 读取一个字典中的完整字典项顺序。
     *
     * @param tenantId 父字典所属租户；空表示平台全局
     * @param dictionaryId 已进入当前管理范围的父字典主键
     * @return 按当前展示顺序排列的轻量字典项和版本
     */
    @NotNull
    List<DictionaryOrderItem> itemOrder(@Nullable Long tenantId, @NotNull Long dictionaryId);

    /**
     * 创建平台全局或租户私有的可配置字典类型。
     *
     * @param tenantId 所属租户；空表示平台全局
     * @param code 不可复用的固定类型编码
     * @param kind 业务编码或输入建议，不能由接口创建代码枚举
     * @param labels 用户维护的中英文名称
     * @return 新建字典类型主键
     */
    @NotNull
    Long createType(
            @Nullable Long tenantId,
            @NotNull String code,
            @NotNull DictionaryKind kind,
            @NotNull Map<String, String> labels);

    /**
     * 在可配置字典类型中创建业务编码项或输入建议项。
     *
     * @param tenantId 父字典所属租户；空表示平台全局
     * @param dictionaryId 父字典类型主键
     * @param value 固定业务编码或自由输入建议文本
     * @param labels 用户维护的中英文标签
     * @param tone 有限含义色调
     * @param selectable 是否允许用于新输入
     * @return 新建字典项主键
     */
    @NotNull
    Long createItem(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @NotNull String value,
            @NotNull Map<String, String> labels,
            @NotNull DictionaryTone tone,
            boolean selectable);

    /**
     * 修改可配置字典的多语言名称和启用状态。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param labels 用户维护的多语言名称
     * @param status 新启用状态
     * @param version 当前乐观锁版本
     */
    void updateType(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @NotNull Map<String, String> labels,
            @NotNull DictionaryStatus status,
            long version);

    /**
     * 修改可配置字典项的内容和启用状态。
     *
     * <p>业务编码值创建后不可变；输入建议值允许修改。代码枚举项不接受该操作。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param itemId 字典项主键
     * @param value 业务固定值或输入建议文本
     * @param labels 用户维护的多语言标签
     * @param tone 新含义色调
     * @param selectable 是否允许用于新输入
     * @param status 新启用状态
     * @param version 当前乐观锁版本
     */
    void updateItem(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @NotNull Long itemId,
            @NotNull String value,
            @NotNull Map<String, String> labels,
            @NotNull DictionaryTone tone,
            boolean selectable,
            @NotNull DictionaryStatus status,
            long version);

    /**
     * 删除一个可配置字典类型及其字典项。
     *
     * <p>代码枚举不能删除。业务编码存在业务模块引用时拒绝删除；输入建议不参与业务关系，可以直接清理其项。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param version 当前乐观锁版本
     */
    void deleteType(@Nullable Long tenantId, @NotNull Long dictionaryId, long version);

    /**
     * 删除一个可配置字典项。
     *
     * <p>被引用的业务编码只能停用；输入建议可以直接删除。代码枚举项不能删除。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param itemId 字典项主键
     * @param version 当前乐观锁版本
     */
    void deleteItem(
            @Nullable Long tenantId, @NotNull Long dictionaryId, @NotNull Long itemId, long version);

    /**
     * 原子保存当前管理范围内全部字典类型的展示顺序。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryVersions 按最终位置排列的完整字典主键和原版本
     */
    void reorderTypes(@Nullable Long tenantId, @NotNull Map<Long, Long> dictionaryVersions);

    /**
     * 原子保存一个字典中全部字典项的展示顺序。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param itemVersions 按最终位置排列的完整字典项主键和原版本
     */
    void reorderItems(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @NotNull Map<Long, Long> itemVersions);
}
