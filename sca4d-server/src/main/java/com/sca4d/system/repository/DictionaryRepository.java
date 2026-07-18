package com.sca4d.system.repository;

import static com.mybatisflex.core.query.QueryMethods.column;
import static com.mybatisflex.core.query.QueryMethods.upper;

import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.access.AccessAction;
import com.sca4d.common.access.ResourceAccess;
import com.sca4d.common.access.ResourceAccessDeniedException;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.dictionary.CodeDictionaryCatalog;
import com.sca4d.common.dictionary.CodeDictionaryDefinition;
import com.sca4d.common.dictionary.CodeDictionaryMode;
import com.sca4d.common.dictionary.CodeDictionaryOption;
import com.sca4d.common.dictionary.DictionarySource;
import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.SysDictionaryItemEntity;
import com.sca4d.entity.SysDictionaryTypeEntity;
import com.sca4d.mapper.SysDictionaryItemMapper;
import com.sca4d.mapper.SystemDictionaryMapper;
import com.sca4d.system.DictionaryKind;
import com.sca4d.system.DictionaryLabelCodec;
import com.sca4d.system.DictionaryOrder;
import com.sca4d.system.DictionaryStatus;
import com.sca4d.system.SystemClientStateTargets;
import com.sca4d.system.SystemErrorCode;
import com.sca4d.system.dictionary.DictionaryItemSnapshot;
import com.sca4d.system.dictionary.DictionarySnapshot;
import com.sca4d.tenant.policy.TenantAccessCriteria;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 读写系统字典类型、字典项和代码字典同步记录。
 *
 * <p>代码目录同步由一次事务锁、一次类型查询、至多一次字典项查询和两次批量 upsert 完成，SQL 数量不随枚举项数量增长。
 */
@Repository
@RequiredArgsConstructor
public class DictionaryRepository {

    private static final String EMPTY_LABELS = "{}";
    private final SystemDictionaryMapper mapper;
    private final SysDictionaryItemMapper itemMapper;
    private final IdGenerator idGenerator;
    private final DictionaryLabelCodec labelCodec;
    private final ResourceAccess resourceAccess;

    /**
     * 串行化一个平台、租户或父字典范围内的创建与排序。
     *
     * @param scope 调用方按业务范围生成的固定锁键
     */
    public void lockOrdering(@NotNull String scope) {
        mapper.lockDictionaryOrder(scope);
    }

    /**
     * 将当前代码字典目录同步为数据库中的来源投影。
     *
     * <p>多节点启动先竞争 PostgreSQL 事务级互斥锁，再重新读取当前投影，因此同一新类型或枚举项只会分配一个主键。闭集定义准确收敛
     * 为代码当前发布项；可扩展定义只接管操作保留值并保留普通配置项。代码声明移除时，闭集投影进入逻辑删除终态，可扩展投影释放为配置
     * 来源。所有已有排序均保持不变。
     *
     * @param catalog 当前应用扫描并校验完成的代码字典目录
     */
    @Transactional
    public void synchronizeCodeCatalog(@NotNull CodeDictionaryCatalog catalog) {
        // 事务锁把多个应用节点的扫描投影串行化；锁后读取保证后启动节点观察到前一节点刚写入的主键和来源变化。
        mapper.lockCodeDictionaryCatalog();
        List<SysDictionaryTypeEntity> existingTypes = mapper.selectGlobalDictionaryTypes();
        Map<String, SysDictionaryTypeEntity> typeByCode = existingTypes.stream()
                .collect(Collectors.toMap(
                        SysDictionaryTypeEntity::getCode,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalStateException("重复全局字典类型编码: " + left.getCode());
                        },
                        LinkedHashMap::new));
        Map<String, CodeDictionaryDefinition> definitionByCode = catalog.definitions().stream()
                .collect(Collectors.toMap(
                        CodeDictionaryDefinition::getCode,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalStateException("重复代码字典定义: " + left.getCode());
                        },
                        LinkedHashMap::new));

        // 先形成每个当前定义的目标类型。闭集不能接管开放字典；可扩展定义可以把同编码 BUSINESS_CODE 接入代码保留值体系。
        List<SysDictionaryTypeEntity> synchronizedTypes = new ArrayList<>();
        for (CodeDictionaryDefinition definition : catalog.definitions()) {
            SysDictionaryTypeEntity existing = typeByCode.get(definition.getCode());
            DictionaryKind expectedKind = definition.getMode() == CodeDictionaryMode.CLOSED
                    ? DictionaryKind.CODE_ENUM
                    : DictionaryKind.BUSINESS_CODE;
            if (existing != null && existing.getKind() != expectedKind) {
                throw new IllegalStateException("代码字典编码已被其他语义类型占用: " + definition.getCode() + "."
                        + existing.getKind().value());
            }
            SysDictionaryTypeEntity synchronizedType = new SysDictionaryTypeEntity()
                    .setId(existing == null ? idGenerator.nextId() : existing.getId())
                    .setCode(definition.getCode())
                    .setKind(expectedKind)
                    .setSource(DictionarySource.CODE)
                    .setNameKey(definition.getNameKey())
                    .setNameLabels(existing == null || definition.getMode() == CodeDictionaryMode.CLOSED
                            ? EMPTY_LABELS
                            : existing.getNameLabels())
                    .setStatus(existing == null || definition.getMode() == CodeDictionaryMode.CLOSED
                            ? DictionaryStatus.ACTIVE
                            : existing.getStatus())
                    .setSortOrder(existing == null ? 0 : existing.getSortOrder())
                    .setSourceRef(definition.getSourceRef())
                    .setDeleted(0);
            synchronizedTypes.add(synchronizedType);
        }

        // 不再存在的闭集保留代码来源并逻辑删除；不再存在的可扩展定义释放为普通配置字典，继续保留业务数据和展示配置。
        for (SysDictionaryTypeEntity existing : existingTypes) {
            if (definitionByCode.containsKey(existing.getCode()) || existing.getSource() != DictionarySource.CODE) {
                continue;
            }
            SysDictionaryTypeEntity converged = new SysDictionaryTypeEntity()
                    .setId(existing.getId())
                    .setCode(existing.getCode())
                    .setKind(existing.getKind())
                    .setSource(existing.getKind() == DictionaryKind.CODE_ENUM
                            ? DictionarySource.CODE
                            : DictionarySource.CONFIGURATION)
                    .setNameKey(existing.getNameKey())
                    .setNameLabels(existing.getNameLabels())
                    .setStatus(existing.getKind() == DictionaryKind.CODE_ENUM
                            ? DictionaryStatus.DISABLED
                            : existing.getStatus())
                    .setSortOrder(existing.getSortOrder())
                    .setSourceRef(existing.getKind() == DictionaryKind.CODE_ENUM ? existing.getSourceRef() : null)
                    .setDeleted(existing.getKind() == DictionaryKind.CODE_ENUM ? 1 : existing.getDeleted());
            synchronizedTypes.add(converged);
        }

        Set<Long> existingTypeIds = existingTypes.stream()
                .map(SysDictionaryTypeEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Long> existingSynchronizedTypeIds = synchronizedTypes.stream()
                .map(SysDictionaryTypeEntity::getId)
                .filter(existingTypeIds::contains)
                .toList();
        List<SysDictionaryItemEntity> existingItems = existingSynchronizedTypeIds.isEmpty()
                ? List.of()
                : mapper.selectDictionaryItems(existingSynchronizedTypeIds);
        Map<Long, Map<String, SysDictionaryItemEntity>> itemByTypeAndValue = new LinkedHashMap<>();
        for (SysDictionaryItemEntity item : existingItems) {
            Map<String, SysDictionaryItemEntity> itemByValue = itemByTypeAndValue.computeIfAbsent(
                    item.getDictTypeId(), ignored -> new LinkedHashMap<>());
            String normalizedValue = item.getItemValue().toUpperCase(Locale.ROOT);
            if (itemByValue.putIfAbsent(normalizedValue, item) != null) {
                throw new IllegalStateException(
                        "代码字典存在大小写冲突的重复值: " + item.getDictTypeId() + "." + item.getItemValue());
            }
        }

        // 在同一内存快照中完成闭集收敛、扩展项接管和来源释放，再用一次批量 upsert 写回，SQL 数量不随字典规模增长。
        List<SysDictionaryItemEntity> synchronizedItems = new ArrayList<>();
        for (SysDictionaryTypeEntity type : synchronizedTypes) {
            CodeDictionaryDefinition definition = definitionByCode.get(type.getCode());
            Map<String, SysDictionaryItemEntity> existingByValue = itemByTypeAndValue.getOrDefault(type.getId(), Map.of());
            if (definition == null) {
                for (SysDictionaryItemEntity existing : existingByValue.values()) {
                    if (existing.getSource() != DictionarySource.CODE) {
                        continue;
                    }
                    boolean closed = type.getKind() == DictionaryKind.CODE_ENUM;
                    synchronizedItems.add(new SysDictionaryItemEntity()
                            .setId(existing.getId())
                            .setDictTypeId(existing.getDictTypeId())
                            .setItemValue(existing.getItemValue())
                            .setSource(closed ? DictionarySource.CODE : DictionarySource.CONFIGURATION)
                            .setSourceRef(closed ? existing.getSourceRef() : null)
                            .setLabelKey(existing.getLabelKey())
                            .setLabelLabels(existing.getLabelLabels())
                            .setTone(existing.getTone())
                            .setSelectable(closed ? false : existing.getSelectable())
                            .setStatus(closed ? DictionaryStatus.DISABLED : existing.getStatus())
                            .setSortOrder(existing.getSortOrder())
                            .setDeleted(closed ? 1 : existing.getDeleted()));
                }
                continue;
            }

            Map<String, CodeDictionaryOption> optionByValue = definition.getOptions().stream()
                    .filter(CodeDictionaryOption::isPublished)
                    .collect(Collectors.toMap(
                            option -> option.getValue().toUpperCase(Locale.ROOT),
                            Function.identity(),
                            (left, right) -> {
                                throw new IllegalStateException(
                                        "重复代码字典枚举值: " + definition.getCode() + "." + left.getValue());
                            },
                            LinkedHashMap::new));

            // 闭集不允许配置来源项，取消发布的代码项进入逻辑删除；可扩展字典则把取消保留的项释放为普通业务编码。
            for (SysDictionaryItemEntity existing : existingByValue.values()) {
                if (optionByValue.containsKey(existing.getItemValue().toUpperCase(Locale.ROOT))) {
                    continue;
                }
                if (definition.getMode() == CodeDictionaryMode.CLOSED
                        && existing.getSource() == DictionarySource.CONFIGURATION) {
                    throw new IllegalStateException("闭集代码字典不能包含配置项: " + type.getCode() + "." + existing.getItemValue());
                }
                if (existing.getSource() == DictionarySource.CODE) {
                    boolean closed = definition.getMode() == CodeDictionaryMode.CLOSED;
                    synchronizedItems.add(new SysDictionaryItemEntity()
                            .setId(existing.getId())
                            .setDictTypeId(existing.getDictTypeId())
                            .setItemValue(existing.getItemValue())
                            .setSource(closed ? DictionarySource.CODE : DictionarySource.CONFIGURATION)
                            .setSourceRef(closed ? existing.getSourceRef() : null)
                            .setLabelKey(existing.getLabelKey())
                            .setLabelLabels(existing.getLabelLabels())
                            .setTone(existing.getTone())
                            .setSelectable(closed ? false : existing.getSelectable())
                            .setStatus(closed ? DictionaryStatus.DISABLED : existing.getStatus())
                            .setSortOrder(existing.getSortOrder())
                            .setDeleted(closed ? 1 : existing.getDeleted()));
                }
            }

            for (CodeDictionaryOption option : definition.getOptions()) {
                if (!option.isPublished()) {
                    continue;
                }
                SysDictionaryItemEntity existing = existingByValue.get(option.getValue().toUpperCase(Locale.ROOT));
                boolean closed = definition.getMode() == CodeDictionaryMode.CLOSED;
                synchronizedItems.add(new SysDictionaryItemEntity()
                        .setId(existing == null ? idGenerator.nextId() : existing.getId())
                        .setDictTypeId(type.getId())
                        .setItemValue(option.getValue())
                        .setSource(DictionarySource.CODE)
                        .setSourceRef(option.getSourceRef())
                        .setLabelKey(option.getLabelKey())
                        .setLabelLabels(existing == null || closed ? EMPTY_LABELS : existing.getLabelLabels())
                        .setTone(existing == null || closed ? option.getTone() : existing.getTone())
                        .setSelectable(existing == null || closed ? option.isSelectable() : existing.getSelectable())
                        .setStatus(existing == null || closed ? DictionaryStatus.ACTIVE : existing.getStatus())
                        .setSortOrder(existing == null ? option.getSortOrder() : existing.getSortOrder())
                        .setDeleted(0));
            }
        }

        // 两次批量 upsert 分别维护父类型和子项；字典数量增加不会增加 SQL 次数。
        if (!synchronizedTypes.isEmpty()) {
            mapper.upsertCodeDictionaryTypes(synchronizedTypes);
        }
        if (!synchronizedItems.isEmpty()) {
            mapper.upsertCodeDictionaryItems(synchronizedItems);
        }
    }

    /**
     * 批量读取平台全局或指定租户实际可见的字典。
     *
     * <p>类型和字典项各执行一次单表查询，总计固定 2 条 SQL。返回顺序完全采用数据库的类型、字典项排序；代码枚举也不回退到 Java
     * 声明顺序。停用字典仍返回全部未删除项，由 Service 共用标记为不可选择，使历史业务值继续可解释；租户与全局出现同名编码时
     * 拒绝返回，避免形成隐式覆盖协议。
     *
     * @param tenantId 租户范围；为空时只读取平台全局字典
     * @param codes 已校验、去重的字典类型编码
     * @return 按数据库展示顺序排列的字典读取快照
     */
    public @NotNull List<DictionarySnapshot> findVisibleSelections(
            @Nullable Long tenantId, @NotNull List<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        QueryWrapper typeQuery = QueryWrapper.create()
                .where(SysDictionaryTypeEntity::getCode)
                .in(codes)
                .and(SysDictionaryTypeEntity::getDeleted)
                .eq(0);
        if (tenantId == null) {
            typeQuery.and(SysDictionaryTypeEntity::getTenantId).isNull();
        } else {
            typeQuery.and((QueryWrapper scope) -> {
                scope.where(SysDictionaryTypeEntity::getTenantId)
                        .isNull()
                        .or(SysDictionaryTypeEntity::getTenantId)
                        .eq(tenantId);
            });
        }
        typeQuery.orderBy(SysDictionaryTypeEntity::getSortOrder)
                .asc()
                .orderBy(SysDictionaryTypeEntity::getId)
                .asc();
        List<SysDictionaryTypeEntity> types = mapper.selectListByQuery(typeQuery);

        Map<String, SysDictionaryTypeEntity> visibleByCode = new LinkedHashMap<>();
        for (SysDictionaryTypeEntity type : types) {
            if (visibleByCode.putIfAbsent(type.getCode(), type) != null) {
                throw new IllegalStateException("租户字典不能覆盖同名平台全局字典: " + type.getCode());
            }
        }
        if (types.isEmpty()) {
            return List.of();
        }

        List<Long> typeIds = types.stream().map(SysDictionaryTypeEntity::getId).toList();
        QueryWrapper itemQuery = QueryWrapper.create()
                .where(SysDictionaryItemEntity::getDictTypeId)
                .in(typeIds)
                .and(SysDictionaryItemEntity::getDeleted)
                .eq(0)
                .orderBy(SysDictionaryItemEntity::getDictTypeId)
                .asc()
                .orderBy(SysDictionaryItemEntity::getSortOrder)
                .asc()
                .orderBy(SysDictionaryItemEntity::getId)
                .asc();
        Map<Long, List<DictionaryItemSnapshot>> itemsByType = new LinkedHashMap<>();
        for (SysDictionaryItemEntity item : itemMapper.selectListByQuery(itemQuery)) {
            itemsByType.computeIfAbsent(item.getDictTypeId(), ignored -> new ArrayList<>())
                    .add(new DictionaryItemSnapshot(
                            item.getItemValue(),
                            item.getLabelKey(),
                            labelCodec.decode(item.getLabelLabels()),
                            item.getTone(),
                            Boolean.TRUE.equals(item.getSelectable()),
                            item.getStatus()));
        }

        return types.stream()
                .map(type -> new DictionarySnapshot(
                        type.getCode(),
                        type.getKind(),
                        type.getStatus(),
                        type.getNameKey(),
                        labelCodec.decode(type.getNameLabels()),
                        itemsByType.getOrDefault(type.getId(), List.of())))
                .toList();
    }

    /**
     * 分页读取一个精确管理范围中的字典类型。
     *
     * <p>MyBatis-Flex 分页固定执行 count 和 records 共 2 条 SQL；关键词只匹配固定类型编码，JSONB 多语言名称搜索将在明确索引
     * 方案后单独实现，不用无索引全表表达式冒充通用搜索。
     *
     * @param tenantId 精确所属租户；空表示平台全局
     * @param search 类型编码关键词；空表示不筛选
     * @param kind 字典业务含义类型；空表示不筛选
     * @param status 启用状态；空表示不筛选
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 字典类型持久化分页
     */
    public @NotNull PageData<SysDictionaryTypeEntity> pageManagedTypes(
            @Nullable Long tenantId,
            @Nullable String search,
            @Nullable DictionaryKind kind,
            @Nullable DictionaryStatus status,
            long page,
            long size) {
        requireTenantAccess(tenantId, AccessAction.READ);
        QueryWrapper query = QueryWrapper.create()
                .where(SysDictionaryTypeEntity::getDeleted)
                .eq(0)
                .and(SysDictionaryTypeEntity::getKind)
                .eq(kind, Objects::nonNull)
                .and(SysDictionaryTypeEntity::getStatus)
                .eq(status, Objects::nonNull);
        if (tenantId == null) {
            query.and(SysDictionaryTypeEntity::getTenantId).isNull();
        } else {
            query.and(SysDictionaryTypeEntity::getTenantId).eq(tenantId);
        }
        if (search != null) {
            query.and(SysDictionaryTypeEntity::getCode).like(search);
        }
        query.orderBy(SysDictionaryTypeEntity::getSortOrder)
                .asc()
                .orderBy(SysDictionaryTypeEntity::getId)
                .asc();
        return PageData.from(mapper.paginate(page, size, query));
    }

    /**
     * 按精确管理范围读取一个字典类型。
     *
     * @param tenantId 精确所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @return 未删除字典类型；范围不匹配或不存在时返回 {@code null}
     */
    public @Nullable SysDictionaryTypeEntity findManagedType(
            @Nullable Long tenantId, @NotNull Long dictionaryId) {
        requireTenantAccess(tenantId, AccessAction.READ);
        QueryWrapper query = QueryWrapper.create()
                .where(SysDictionaryTypeEntity::getId)
                .eq(dictionaryId)
                .and(SysDictionaryTypeEntity::getDeleted)
                .eq(0);
        if (tenantId == null) {
            query.and(SysDictionaryTypeEntity::getTenantId).isNull();
        } else {
            query.and(SysDictionaryTypeEntity::getTenantId).eq(tenantId);
        }
        return mapper.selectOneByQuery(query);
    }

    /**
     * 批量读取资源引用所需的字典类型安全投影。
     *
     * <p>固定执行一条单表 SQL，包含逻辑删除状态以生成明确删除态；不加载全部字典项。
     *
     * @param dictionaryIds 直接引用或字典项父关系涉及的字典主键
     * @return 字典主键、租户、编码、名称消息键、多语言标签和删除状态
     */
    public @NotNull List<SysDictionaryTypeEntity> findTypeReferences(@NotNull Set<Long> dictionaryIds) {
        if (dictionaryIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(SysDictionaryTypeEntity::getId),
                        column(SysDictionaryTypeEntity::getTenantId),
                        column(SysDictionaryTypeEntity::getCode),
                        column(SysDictionaryTypeEntity::getNameKey),
                        column(SysDictionaryTypeEntity::getNameLabels).cast("text").as(SysDictionaryTypeEntity::getNameLabels),
                        column(SysDictionaryTypeEntity::getDeleted))
                .where(SysDictionaryTypeEntity::getId)
                .in(dictionaryIds);
        return mapper.selectListByQuery(query);
    }

    /**
     * 批量读取资源引用所需的字典项安全投影。
     *
     * <p>固定执行一条单表 SQL；父字典作用域由贡献者合并后再用一条类型查询完成，不逐项查询。
     *
     * @param itemIds 去重后的字典项主键
     * @return 字典项主键、父主键、固定值、标签消息键、多语言标签和删除状态
     */
    public @NotNull List<SysDictionaryItemEntity> findItemReferences(@NotNull Set<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(SysDictionaryItemEntity::getId),
                        column(SysDictionaryItemEntity::getDictTypeId),
                        column(SysDictionaryItemEntity::getItemValue),
                        column(SysDictionaryItemEntity::getLabelKey),
                        column(SysDictionaryItemEntity::getLabelLabels).cast("text").as(SysDictionaryItemEntity::getLabelLabels),
                        column(SysDictionaryItemEntity::getDeleted))
                .where(SysDictionaryItemEntity::getId)
                .in(itemIds);
        return itemMapper.selectListByQuery(query);
    }

    /**
     * 在当前事务中锁定并读取一个精确管理范围中的字典类型。
     *
     * <p>类型内容变更、删除和子项写入使用同一父行锁串行化。锁持有到调用方事务结束，保证父字典不会在业务规则检查与最终写入之间改变
     * 全部状态；固定执行 1 条 SQL。
     *
     * @param tenantId 精确所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @return 已锁定的未删除字典类型；范围不匹配或不存在时返回 {@code null}
     */
    public @Nullable SysDictionaryTypeEntity findManagedTypeForUpdate(
            @Nullable Long tenantId, @NotNull Long dictionaryId) {
        requireTenantAccess(tenantId, AccessAction.WRITE);
        return mapper.selectDictionaryTypeForUpdate(tenantId, dictionaryId);
    }

    /**
     * 读取一个未删除字典类型中的未删除字典项。
     *
     * <p>父字典范围由调用方在同一业务操作中校验，本查询只表达固定的父子关系，不重复拼接租户条件。
     *
     * @param dictionaryId 已完成管理范围校验的父字典主键
     * @param itemId 字典项主键
     * @return 匹配的未删除字典项；不存在时返回 {@code null}
     */
    public @Nullable SysDictionaryItemEntity findManagedItem(
            @NotNull Long dictionaryId, @NotNull Long itemId) {
        QueryWrapper query = QueryWrapper.create()
                .where(SysDictionaryItemEntity::getId)
                .eq(itemId)
                .and(SysDictionaryItemEntity::getDictTypeId)
                .eq(dictionaryId)
                .and(SysDictionaryItemEntity::getDeleted)
                .eq(0);
        return itemMapper.selectOneByQuery(query);
    }

    /**
     * 读取业务字典中仍保留的全部固定值，用于一次批量引用检查。
     *
     * <p>固定执行 1 条 SQL；已停用项仍可能被历史业务记录引用，因此不能只读取 ACTIVE 项。
     *
     * @param dictionaryId 已完成管理范围和业务类型校验的字典主键
     * @return 去重后的未删除字典项值
     */
    public @NotNull Set<String> findManagedItemValues(@NotNull Long dictionaryId) {
        QueryWrapper query = QueryWrapper.create()
                .select(column(SysDictionaryItemEntity::getItemValue))
                .where(SysDictionaryItemEntity::getDictTypeId)
                .eq(dictionaryId)
                .and(SysDictionaryItemEntity::getDeleted)
                .eq(0);
        return itemMapper.selectObjectListByQueryAs(query, String.class).stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 分页读取一个已完成范围校验的字典类型中的字典项。
     *
     * <p>分页固定执行 count 和 records 共 2 条 SQL；调用方应先用一次类型查询确认父字典属于当前管理范围，总调用数固定为 3。
     *
     * @param dictionaryId 已完成范围校验的字典类型主键
     * @param search 固定值或建议文本关键词；空表示不筛选
     * @param status 启用状态；空表示不筛选
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 字典项持久化分页
     */
    public @NotNull PageData<SysDictionaryItemEntity> pageManagedItems(
            @NotNull Long dictionaryId,
            @Nullable String search,
            @Nullable DictionaryStatus status,
            long page,
            long size) {
        QueryWrapper query = QueryWrapper.create()
                .where(SysDictionaryItemEntity::getDictTypeId)
                .eq(dictionaryId)
                .and(SysDictionaryItemEntity::getDeleted)
                .eq(0)
                .and(SysDictionaryItemEntity::getStatus)
                .eq(status, Objects::nonNull);
        if (search != null) {
            query.and(SysDictionaryItemEntity::getItemValue).like(search);
        }
        query.orderBy(SysDictionaryItemEntity::getSortOrder)
                .asc()
                .orderBy(SysDictionaryItemEntity::getId)
                .asc();
        return PageData.from(itemMapper.paginate(page, size, query));
    }

    /**
     * 读取当前精确管理范围中的完整字典类型顺序。
     *
     * <p>固定执行一条单表 SQL，只选择拖拽展示、本地化和乐观提交需要的字段。
     *
     * @param tenantId 精确所属租户；空表示平台全局
     * @return 按内部排序值和主键固定排列的未删除字典类型
     */
    public @NotNull List<SysDictionaryTypeEntity> findManagedTypesForOrdering(@Nullable Long tenantId) {
        requireTenantAccess(tenantId, AccessAction.READ);
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(SysDictionaryTypeEntity::getId),
                        column(SysDictionaryTypeEntity::getCode),
                        column(SysDictionaryTypeEntity::getNameKey),
                        column(SysDictionaryTypeEntity::getNameLabels).cast("text").as(SysDictionaryTypeEntity::getNameLabels),
                        column(SysDictionaryTypeEntity::getVersion))
                .where(SysDictionaryTypeEntity::getDeleted)
                .eq(0);
        if (tenantId == null) {
            query.and(SysDictionaryTypeEntity::getTenantId).isNull();
        } else {
            query.and(SysDictionaryTypeEntity::getTenantId).eq(tenantId);
        }
        query.orderBy(SysDictionaryTypeEntity::getSortOrder)
                .asc()
                .orderBy(SysDictionaryTypeEntity::getId)
                .asc();
        return mapper.selectListByQuery(query);
    }

    /**
     * 读取一个已完成父范围校验的字典中的完整字典项顺序。
     *
     * <p>固定执行一条单表 SQL，只选择拖拽展示、本地化和乐观提交需要的字段。
     *
     * @param dictionaryId 已完成管理范围校验的父字典主键
     * @return 按内部排序值和主键固定排列的未删除字典项
     */
    public @NotNull List<SysDictionaryItemEntity> findManagedItemsForOrdering(@NotNull Long dictionaryId) {
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(SysDictionaryItemEntity::getId),
                        column(SysDictionaryItemEntity::getDictTypeId),
                        column(SysDictionaryItemEntity::getItemValue),
                        column(SysDictionaryItemEntity::getLabelKey),
                        column(SysDictionaryItemEntity::getLabelLabels).cast("text").as(SysDictionaryItemEntity::getLabelLabels),
                        column(SysDictionaryItemEntity::getVersion))
                .where(SysDictionaryItemEntity::getDictTypeId)
                .eq(dictionaryId)
                .and(SysDictionaryItemEntity::getDeleted)
                .eq(0)
                .orderBy(SysDictionaryItemEntity::getSortOrder)
                .asc()
                .orderBy(SysDictionaryItemEntity::getId)
                .asc();
        return itemMapper.selectListByQuery(query);
    }

    /**
     * 创建平台全局或租户私有的可配置字典类型。
     *
     * <p>事务先按类型编码获取 PostgreSQL advisory lock，再用 1 条类型查询检查包括已停用记录在内的占用：平台全局编码不能与任何租户同名，
     * 租户编码不能覆盖全局或本租户历史编码，不同租户之间可以使用相同编码。Repository 分配全局主键后调用 JSONB 专用插入 SQL，
     * 避免把序列化标签按 VARCHAR 写入 PostgreSQL。
     *
     * @param tenantId 所属租户；空表示平台全局
     * @param code 已校验的固定类型编码
     * @param kind 业务编码或输入建议
     * @param labels 已校验的用户多语言名称
     * @return 新建字典类型主键
     */
    @Transactional
    public @NotNull Long createConfigurableType(
            @Nullable Long tenantId,
            @NotNull String code,
            @NotNull DictionaryKind kind,
            @NotNull Map<String, String> labels) {
        requireTenantAccess(tenantId, AccessAction.WRITE);
        mapper.lockDictionaryTypeCode(code);
        QueryWrapper occupiedQuery = QueryWrapper.create()
                .where(SysDictionaryTypeEntity::getCode)
                .eq(code);
        List<SysDictionaryTypeEntity> occupied = mapper.selectListByQuery(occupiedQuery);
        boolean conflict = occupied.stream().anyMatch(existing -> tenantId == null
                || existing.getTenantId() == null
                || tenantId.equals(existing.getTenantId()));
        if (conflict) {
            throw new BizException(SystemErrorCode.DICTIONARY_CODE_OCCUPIED);
        }
        Integer currentMaximum = mapper.selectMaxDictionaryTypeSortOrder(tenantId);
        int sortOrder = Math.addExact(currentMaximum == null ? 0 : currentMaximum, 10);

        SysDictionaryTypeEntity entity = new SysDictionaryTypeEntity()
                .setId(idGenerator.nextId())
                .setTenantId(tenantId)
                .setCode(code)
                .setKind(kind)
                .setSource(DictionarySource.CONFIGURATION)
                .setNameKey(null)
                .setNameLabels(labelCodec.encode(labels))
                .setStatus(DictionaryStatus.ACTIVE)
                .setSortOrder(sortOrder)
                .setSourceRef(null)
                .setDeleted(0);
        if (mapper.insertConfigurableDictionaryType(entity) != 1) {
            throw new IllegalStateException("字典类型写入行数异常");
        }
        return entity.getId();
    }

    /**
     * 在一个已完成范围校验的可配置字典中创建字典项。
     *
     * <p>事务按父字典和值获取 PostgreSQL advisory lock，再用 1 条查询检查包括已停用记录在内的唯一性。Repository 分配全局主键后调用
     * JSONB 专用插入 SQL；逻辑删除值仍占用契约，不能被静默复用。
     *
     * @param dictionaryId 已完成范围和类型校验的父字典主键
     * @param value 已按父字典含义校验的固定值或建议文本
     * @param labels 已校验的用户多语言标签
     * @param tone 有限含义色调
     * @param selectable 是否允许用于新输入
     * @return 新建字典项主键
     */
    @Transactional
    public @NotNull Long createConfigurableItem(
            @NotNull Long dictionaryId,
            @NotNull String value,
            @NotNull Map<String, String> labels,
            @NotNull DictionaryTone tone,
            boolean selectable) {
        mapper.lockDictionaryItemValue(dictionaryId, value);
        QueryWrapper occupiedQuery = QueryWrapper.create()
                .where(SysDictionaryItemEntity::getDictTypeId)
                .eq(dictionaryId)
                .and(upper(SysDictionaryItemEntity::getItemValue).eq(value.toUpperCase(Locale.ROOT)));
        if (itemMapper.selectCountByQuery(occupiedQuery) > 0) {
            throw new BizException(SystemErrorCode.DICTIONARY_ITEM_VALUE_OCCUPIED);
        }
        Integer currentMaximum = mapper.selectMaxDictionaryItemSortOrder(dictionaryId);
        int sortOrder = Math.addExact(currentMaximum == null ? 0 : currentMaximum, 10);

        SysDictionaryItemEntity entity = new SysDictionaryItemEntity()
                .setId(idGenerator.nextId())
                .setDictTypeId(dictionaryId)
                .setItemValue(value)
                .setSource(DictionarySource.CONFIGURATION)
                .setSourceRef(null)
                .setLabelKey(null)
                .setLabelLabels(labelCodec.encode(labels))
                .setTone(tone)
                .setSelectable(selectable)
                .setStatus(DictionaryStatus.ACTIVE)
                .setSortOrder(sortOrder)
                .setDeleted(0);
        if (mapper.insertConfigurableDictionaryItem(entity) != 1) {
            throw new IllegalStateException("字典项写入行数异常");
        }
        return entity.getId();
    }

    /**
     * 原子修改可配置字典的多语言名称和启用状态。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param labels 已校验的多语言名称
     * @param status 新启用状态
     * @param version 当前乐观锁版本
     */
    public void updateConfigurableType(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @NotNull Map<String, String> labels,
            @NotNull DictionaryStatus status,
            long version) {
        requireTenantAccess(tenantId, AccessAction.WRITE);
        int affected = mapper.updateConfigurableDictionaryType(
                tenantId, dictionaryId, labelCodec.encode(labels), status, version);
        if (affected != 1) {
            throw new ConcurrencyConflictException(SystemErrorCode.DICTIONARY_CHANGED, SystemClientStateTargets.DICTIONARIES);
        }
    }

    /**
     * 原子修改可配置字典项；输入建议值变化时先串行检查包括已停用记录在内的唯一性。
     *
     * <p>输入建议被物理删除后原值可以复用。业务编码值不可变，由 Service 在进入本方法前保证。SQL 原子约束父字典范围、
     * 含义类型、父子关系和乐观锁版本。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param itemId 字典项主键
     * @param kind 可配置字典含义类型
     * @param value 已校验的新值
     * @param labels 已校验的多语言标签
     * @param tone 新含义色调
     * @param selectable 是否可用于新输入
     * @param status 新启用状态
     * @param version 当前乐观锁版本
     */
    @Transactional
    public void updateConfigurableItem(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @NotNull Long itemId,
            @NotNull DictionaryKind kind,
            @NotNull String value,
            @NotNull Map<String, String> labels,
            @NotNull DictionaryTone tone,
            boolean selectable,
            @NotNull DictionaryStatus status,
            long version) {
        requireTenantAccess(tenantId, AccessAction.WRITE);
        if (kind == DictionaryKind.INPUT_SUGGESTION) {
            mapper.lockDictionaryItemValue(dictionaryId, value);
            QueryWrapper occupiedQuery = QueryWrapper.create()
                    .where(SysDictionaryItemEntity::getDictTypeId)
                    .eq(dictionaryId)
                    .and(upper(SysDictionaryItemEntity::getItemValue).eq(value.toUpperCase(Locale.ROOT)))
                    .and(SysDictionaryItemEntity::getId)
                    .ne(itemId);
            if (itemMapper.selectCountByQuery(occupiedQuery) > 0) {
                throw new BizException(SystemErrorCode.DICTIONARY_ITEM_VALUE_OCCUPIED);
            }
        }
        int affected = mapper.updateConfigurableDictionaryItem(
                tenantId,
                dictionaryId,
                itemId,
                kind,
                value,
                labelCodec.encode(labels),
                tone,
                selectable,
                status,
                version);
        if (affected != 1) {
            throw new ConcurrencyConflictException(SystemErrorCode.DICTIONARY_ITEM_CHANGED, SystemClientStateTargets.DICTIONARIES);
        }
    }

    /**
     * 删除一个已确认无业务引用的可配置字典类型。
     *
     * <p>业务编码项进入逻辑删除终态以永久保留固定值；输入建议不承担关系契约，可以物理删除。子项处理和父类型版本更新位于同一事务，
     * 父类型更新失败时全部回滚。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param kind 可配置字典含义类型
     * @param version 当前乐观锁版本
     */
    @Transactional
    public void deleteConfigurableType(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @NotNull DictionaryKind kind,
            long version) {
        requireTenantAccess(tenantId, AccessAction.MANAGE);
        if (kind == DictionaryKind.BUSINESS_CODE) {
            mapper.markDictionaryItemsDeleted(dictionaryId);
        } else if (kind == DictionaryKind.INPUT_SUGGESTION) {
            mapper.deleteDictionaryItems(dictionaryId);
        } else {
            throw new IllegalArgumentException("代码枚举不能通过管理接口删除");
        }
        if (mapper.markDictionaryTypeDeleted(tenantId, dictionaryId, kind, version) != 1) {
            throw new ConcurrencyConflictException(SystemErrorCode.DICTIONARY_CHANGED, SystemClientStateTargets.DICTIONARIES);
        }
    }

    /**
     * 删除一个已确认无业务引用的可配置字典项。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param itemId 字典项主键
     * @param kind 可配置字典含义类型
     * @param version 当前乐观锁版本
     */
    public void deleteConfigurableItem(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @NotNull Long itemId,
            @NotNull DictionaryKind kind,
            long version) {
        requireTenantAccess(tenantId, AccessAction.MANAGE);
        int affected;
        if (kind == DictionaryKind.BUSINESS_CODE) {
            affected = mapper.markBusinessDictionaryItemDeleted(tenantId, dictionaryId, itemId, version);
        } else if (kind == DictionaryKind.INPUT_SUGGESTION) {
            affected = mapper.deleteInputSuggestionItem(tenantId, dictionaryId, itemId, version);
        } else {
            throw new IllegalArgumentException("代码枚举不能通过管理接口删除");
        }
        if (affected != 1) {
            throw new ConcurrencyConflictException(SystemErrorCode.DICTIONARY_ITEM_CHANGED, SystemClientStateTargets.DICTIONARIES);
        }
    }

    /**
     * 使用各资源原版本一次写入当前管理范围中的完整字典类型顺序。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param orders 已按最终位置分配内部排序值的完整集合
     */
    public void reorderTypes(@Nullable Long tenantId, @NotNull List<DictionaryOrder> orders) {
        requireTenantAccess(tenantId, AccessAction.WRITE);
        if (mapper.reorderDictionaryTypes(tenantId, orders) != orders.size()) {
            throw new ConcurrencyConflictException(SystemErrorCode.DICTIONARY_SORT_CHANGED, SystemClientStateTargets.DICTIONARIES);
        }
    }

    /**
     * 使用各资源原版本一次写入一个字典中的完整字典项顺序。
     *
     * @param tenantId 字典所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @param orders 已按最终位置分配内部排序值的完整集合
     */
    public void reorderItems(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @NotNull List<DictionaryOrder> orders) {
        requireTenantAccess(tenantId, AccessAction.WRITE);
        if (mapper.reorderDictionaryItems(tenantId, dictionaryId, orders) != orders.size()) {
            throw new ConcurrencyConflictException(SystemErrorCode.DICTIONARY_ITEM_SORT_CHANGED, SystemClientStateTargets.DICTIONARIES);
        }
    }

    /**
     * 同步隔离指定租户的全部私有字典。
     *
     * <p>固定执行两条批量 SQL，先隔离字典项再隔离类型；不按字典数量循环，也不执行业务引用检查。租户删除是
     * 整个归属空间的终态，历史业务引用由审计和业务快照负责保留。
     *
     * @param tenantId 已进入删除终态的租户主键
     */
    public void isolateTenantDictionaries(@NotNull Long tenantId) {
        mapper.isolateTenantDictionaryItems(tenantId);
        mapper.isolateTenantDictionaryTypes(tenantId);
    }

    /**
     * 硬删除已经同步隔离的租户私有字典。
     *
     * @param tenantId 已进入删除终态的租户主键
     */
    public void deleteIsolatedTenantDictionaries(@NotNull Long tenantId) {
        mapper.deleteIsolatedTenantDictionaries(tenantId);
    }

    /**
     * 确认当前请求可以在指定租户归属字典中执行操作。
     *
     * <p>平台全局字典不属于租户数据范围，仍由独立的平台权限和路由保护。租户私有字典必须同时满足 Controller
     * 声明的请求级范围与当前 Repository 操作要求的访问等级。
     *
     * @param tenantId 字典所属租户；空表示平台全局字典
     * @param requiredAction 当前持久化操作要求的最低访问等级
     */
    private void requireTenantAccess(@Nullable Long tenantId, @NotNull AccessAction requiredAction) {
        if (tenantId == null) {
            return;
        }
        TenantAccessCriteria criteria = resourceAccess.require(TenantAccessCriteria.class);
        if (!criteria.getAction().permits(requiredAction)
                || (criteria.getTargetTenantId() != null && !criteria.getTargetTenantId().equals(tenantId))
                || (criteria.isAllData()
                        ? criteria.getExcludedTenantIds().contains(tenantId)
                        : !criteria.getTenantIds().contains(tenantId))) {
            throw new ResourceAccessDeniedException();
        }
    }
}
