package com.sca4d.system.service;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.dictionary.DictionarySource;
import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.i18n.SupportedLocale;
import com.sca4d.common.validation.ValidationPatterns;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.SysDictionaryItemEntity;
import com.sca4d.entity.SysDictionaryTypeEntity;
import com.sca4d.system.DictionaryKind;
import com.sca4d.system.DictionaryLabelCodec;
import com.sca4d.system.DictionaryOrder;
import com.sca4d.system.DictionaryReferenceRegistry;
import com.sca4d.system.DictionaryService;
import com.sca4d.system.DictionaryStatus;
import com.sca4d.system.SystemClientStateTargets;
import com.sca4d.system.SystemErrorCode;
import com.sca4d.system.dictionary.DictionaryItemSnapshot;
import com.sca4d.system.dictionary.DictionaryOption;
import com.sca4d.system.dictionary.DictionaryOrderItem;
import com.sca4d.system.dictionary.DictionarySelection;
import com.sca4d.system.dictionary.DictionarySnapshot;
import com.sca4d.system.dictionary.ManagedDictionary;
import com.sca4d.system.dictionary.ManagedDictionaryItem;
import com.sca4d.system.repository.DictionaryRepository;
import com.sca4d.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 使用当前请求语言组装平台或租户可见字典选择项的默认实现。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultDictionaryService implements DictionaryService {

    private static final int MAX_BATCH_SIZE = 50;
    private final DictionaryRepository repository;
    private final LocalizedMessages messages;
    private final DictionaryLabelCodec labelCodec;
    private final DictionaryReferenceRegistry referenceRegistry;
    private final TenantService tenantService;

    @Override
    public @NotNull List<DictionarySelection> selections(
            @Nullable Long tenantId, @NotNull List<String> codes) {
        if (tenantId != null) {
            tenantService.require(tenantId);
        }
        Set<String> distinctCodes = new LinkedHashSet<>();
        for (String code : codes) {
            if (code == null || !ValidationPatterns.isNamespacedCode(code)) {
                throw new BizException(SystemErrorCode.DICTIONARY_CODE_INVALID);
            }
            distinctCodes.add(code);
        }
        if (distinctCodes.size() > MAX_BATCH_SIZE) {
            throw new BizException(SystemErrorCode.DICTIONARY_BATCH_SIZE_EXCEEDED, MAX_BATCH_SIZE);
        }

        Locale locale = LocaleContextHolder.getLocale();
        List<DictionarySelection> selections = new ArrayList<>(distinctCodes.size());
        for (DictionarySnapshot dictionary : repository.findVisibleSelections(tenantId, List.copyOf(distinctCodes))) {
            String configuredLabel = dictionary.getLabels().find(locale);
            String label = configuredLabel != null
                    ? configuredLabel
                    : dictionary.getNameKey() == null
                            ? dictionary.getLabels().resolve(locale, dictionary.getCode())
                            : messages.get(locale, dictionary.getNameKey());
            List<DictionaryOption> options = dictionary.getItems().stream()
                    .map(item -> option(dictionary, item, locale))
                    .toList();
            selections.add(new DictionarySelection(
                    dictionary.getCode(), dictionary.getKind(), dictionary.getNameKey(), label, options));
        }
        return List.copyOf(selections);
    }

    @Override
    public @NotNull PageData<ManagedDictionary> pageManagedTypes(
            @Nullable Long tenantId,
            @Nullable String search,
            @Nullable DictionaryKind kind,
            @Nullable DictionaryStatus status,
            long page,
            long size) {
        if (tenantId != null) {
            tenantService.require(tenantId);
        }
        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : null;
        return repository.pageManagedTypes(tenantId, normalizedSearch, kind, status, page, size)
                .map(this::managedType);
    }

    @Override
    public @NotNull ManagedDictionary requireManagedType(
            @Nullable Long tenantId, @NotNull Long dictionaryId) {
        if (tenantId != null) {
            tenantService.require(tenantId);
        }
        SysDictionaryTypeEntity entity = repository.findManagedType(tenantId, dictionaryId);
        if (entity == null) {
            throw new BizException(SystemErrorCode.DICTIONARY_NOT_FOUND);
        }
        return managedType(entity);
    }

    @Override
    public @NotNull PageData<ManagedDictionaryItem> pageManagedItems(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @Nullable String search,
            @Nullable DictionaryStatus status,
            long page,
            long size) {
        requireManagedType(tenantId, dictionaryId);
        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : null;
        return repository.pageManagedItems(dictionaryId, normalizedSearch, status, page, size)
                .map(this::managedItem);
    }

    @Override
    public @NotNull List<DictionaryOrderItem> typeOrder(@Nullable Long tenantId) {
        if (tenantId != null) {
            tenantService.require(tenantId);
        }
        Locale locale = LocaleContextHolder.getLocale();
        List<DictionaryOrderItem> order = new ArrayList<>();
        for (SysDictionaryTypeEntity entity : repository.findManagedTypesForOrdering(tenantId)) {
            var configuredLabels = labelCodec.decode(entity.getNameLabels());
            String configuredLabel = configuredLabels.find(locale);
            String label = configuredLabel != null
                    ? configuredLabel
                    : entity.getNameKey() == null
                            ? configuredLabels.resolve(locale, entity.getCode())
                            : messages.get(locale, entity.getNameKey());
            order.add(new DictionaryOrderItem(
                    entity.getId(), label, entity.getCode(), entity.getVersion()));
        }
        return List.copyOf(order);
    }

    @Override
    public @NotNull List<DictionaryOrderItem> itemOrder(
            @Nullable Long tenantId, @NotNull Long dictionaryId) {
        requireManagedType(tenantId, dictionaryId);
        Locale locale = LocaleContextHolder.getLocale();
        List<DictionaryOrderItem> order = new ArrayList<>();
        for (SysDictionaryItemEntity entity : repository.findManagedItemsForOrdering(dictionaryId)) {
            var configuredLabels = labelCodec.decode(entity.getLabelLabels());
            String configuredLabel = configuredLabels.find(locale);
            String label = configuredLabel != null
                    ? configuredLabel
                    : entity.getLabelKey() == null
                            ? configuredLabels.resolve(locale, entity.getItemValue())
                            : messages.get(locale, entity.getLabelKey());
            order.add(new DictionaryOrderItem(
                    entity.getId(), label, entity.getItemValue(), entity.getVersion()));
        }
        return List.copyOf(order);
    }

    @Override
    @Transactional
    public @NotNull Long createType(
            @Nullable Long tenantId,
            @NotNull String code,
            @NotNull DictionaryKind kind,
            @NotNull Map<String, String> labels) {
        if (tenantId != null) {
            tenantService.lockForWrite(tenantId);
        }
        repository.lockOrdering("types:" + (tenantId == null ? "platform" : tenantId));
        if (!ValidationPatterns.isNamespacedCode(code)) {
            throw new BizException(SystemErrorCode.DICTIONARY_CODE_INVALID);
        }
        if (kind == DictionaryKind.CODE_ENUM) {
            throw new BizException(SystemErrorCode.DICTIONARY_CODE_ENUM_TYPE_CODE_OWNED);
        }
        Long dictionaryId = repository.createConfigurableType(tenantId, code, kind, checkedLabels(labels));
        log.info(
                "字典类型创建已写入事务 | dictionaryId={} tenantId={} kind={}",
                dictionaryId,
                tenantId,
                kind);
        return dictionaryId;
    }

    @Override
    @Transactional
    public @NotNull Long createItem(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @NotNull String value,
            @NotNull Map<String, String> labels,
            @NotNull DictionaryTone tone,
            boolean selectable) {
        if (tenantId != null) {
            tenantService.lockForWrite(tenantId);
        }
        repository.lockOrdering("items:" + dictionaryId);
        ManagedDictionary dictionary = requireManagedTypeForUpdate(tenantId, dictionaryId);
        if (dictionary.getKind() == DictionaryKind.CODE_ENUM) {
            throw new BizException(SystemErrorCode.DICTIONARY_CODE_ENUM_ITEM_CODE_OWNED);
        }
        String checkedValue = value.trim();
        if (checkedValue.isEmpty()) {
            throw new BizException(SystemErrorCode.DICTIONARY_ITEM_VALUE_REQUIRED);
        }
        if (dictionary.getKind() == DictionaryKind.BUSINESS_CODE
                && !ValidationPatterns.isBizCode(checkedValue)) {
            throw new BizException(SystemErrorCode.DICTIONARY_BUSINESS_VALUE_INVALID);
        }
        Long itemId = repository.createConfigurableItem(
                dictionaryId, checkedValue, checkedLabels(labels), tone, selectable);
        log.info(
                "字典项创建已写入事务 | dictionaryId={} itemId={} tenantId={} selectable={}",
                dictionaryId,
                itemId,
                tenantId,
                selectable);
        return itemId;
    }

    @Override
    @Transactional
    public void updateType(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @NotNull Map<String, String> labels,
            @NotNull DictionaryStatus status,
            long version) {
        if (tenantId != null) {
            tenantService.lockForWrite(tenantId);
        }
        ManagedDictionary dictionary = requireManagedTypeForUpdate(tenantId, dictionaryId);
        if (dictionary.getKind() == DictionaryKind.CODE_ENUM) {
            throw new BizException(SystemErrorCode.DICTIONARY_CODE_ENUM_TYPE_READONLY);
        }
        repository.updateConfigurableType(tenantId, dictionaryId, checkedLabels(labels), status, version);
        log.info(
                "字典类型更新已写入事务 | dictionaryId={} tenantId={} status={} version={}",
                dictionaryId,
                tenantId,
                status,
                version + 1);
    }

    @Override
    @Transactional
    public void updateItem(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @NotNull Long itemId,
            @NotNull String value,
            @NotNull Map<String, String> labels,
            @NotNull DictionaryTone tone,
            boolean selectable,
            @NotNull DictionaryStatus status,
            long version) {
        if (tenantId != null) {
            tenantService.lockForWrite(tenantId);
        }
        ManagedDictionary dictionary = requireManagedTypeForUpdate(tenantId, dictionaryId);
        if (dictionary.getKind() == DictionaryKind.CODE_ENUM) {
            throw new BizException(SystemErrorCode.DICTIONARY_CODE_ENUM_ITEM_READONLY);
        }
        SysDictionaryItemEntity item = requireManagedItem(dictionaryId, itemId);
        String checkedValue = value.trim();
        if (checkedValue.isEmpty()) {
            throw new BizException(SystemErrorCode.DICTIONARY_ITEM_VALUE_REQUIRED);
        }
        if (dictionary.getKind() == DictionaryKind.BUSINESS_CODE) {
            if (!item.getItemValue().equals(checkedValue)) {
                throw new BizException(SystemErrorCode.DICTIONARY_BUSINESS_VALUE_IMMUTABLE);
            }
            if (!ValidationPatterns.isBizCode(checkedValue)) {
                throw new BizException(SystemErrorCode.DICTIONARY_BUSINESS_VALUE_INVALID);
            }
        }
        repository.updateConfigurableItem(
                tenantId,
                dictionaryId,
                itemId,
                dictionary.getKind(),
                checkedValue,
                checkedLabels(labels),
                tone,
                selectable,
                status,
                version);
        log.info(
                "字典项更新已写入事务 | dictionaryId={} itemId={} tenantId={} status={} version={}",
                dictionaryId,
                itemId,
                tenantId,
                status,
                version + 1);
    }

    @Override
    @Transactional
    public void deleteType(@Nullable Long tenantId, @NotNull Long dictionaryId, long version) {
        if (tenantId != null) {
            tenantService.lockForWrite(tenantId);
        }
        ManagedDictionary dictionary = requireManagedTypeForUpdate(tenantId, dictionaryId);
        if (dictionary.getSource() == DictionarySource.CODE) {
            throw new BizException(SystemErrorCode.DICTIONARY_CODE_TYPE_DELETE_FORBIDDEN);
        }
        if (dictionary.getKind() == DictionaryKind.BUSINESS_CODE) {
            Set<String> values = repository.findManagedItemValues(dictionaryId);
            Set<String> referenced = referenceRegistry.referencedValues(tenantId, dictionary.getCode(), values);
            if (!referenced.isEmpty()) {
                throw new BizException(SystemErrorCode.DICTIONARY_TYPE_REFERENCED);
            }
        }
        repository.deleteConfigurableType(tenantId, dictionaryId, dictionary.getKind(), version);
        log.info("字典类型删除已写入事务 | dictionaryId={} tenantId={}", dictionaryId, tenantId);
    }

    @Override
    @Transactional
    public void deleteItem(
            @Nullable Long tenantId, @NotNull Long dictionaryId, @NotNull Long itemId, long version) {
        if (tenantId != null) {
            tenantService.lockForWrite(tenantId);
        }
        ManagedDictionary dictionary = requireManagedTypeForUpdate(tenantId, dictionaryId);
        if (dictionary.getKind() == DictionaryKind.CODE_ENUM) {
            throw new BizException(SystemErrorCode.DICTIONARY_CODE_ENUM_ITEM_DELETE_FORBIDDEN);
        }
        SysDictionaryItemEntity item = requireManagedItem(dictionaryId, itemId);
        if (item.getSource() == DictionarySource.CODE) {
            throw new BizException(SystemErrorCode.DICTIONARY_CODE_ITEM_DELETE_FORBIDDEN);
        }
        if (dictionary.getKind() == DictionaryKind.BUSINESS_CODE) {
            Set<String> referenced = referenceRegistry.referencedValues(
                    tenantId, dictionary.getCode(), Set.of(item.getItemValue()));
            if (!referenced.isEmpty()) {
                throw new BizException(SystemErrorCode.DICTIONARY_ITEM_REFERENCED);
            }
        }
        repository.deleteConfigurableItem(tenantId, dictionaryId, itemId, dictionary.getKind(), version);
        log.info(
                "字典项删除已写入事务 | dictionaryId={} itemId={} tenantId={}",
                dictionaryId,
                itemId,
                tenantId);
    }

    /**
     * 把一个字典项快照转换为当前请求语言的共用选项。
     *
     * @param dictionary 所属字典类型，用于形成最终回退文本
     * @param item 当前字典项持久化快照
     * @param locale 当前请求语言
     * @return 包含固定值、本地化标签、色调和禁用状态的共用选项
     */
    private @NotNull DictionaryOption option(
            @NotNull DictionarySnapshot dictionary,
            @NotNull DictionaryItemSnapshot item,
            @NotNull Locale locale) {
        String configuredLabel = item.getLabels().find(locale);
        String label = configuredLabel != null
                ? configuredLabel
                : item.getLabelKey() == null
                        ? item.getLabels().resolve(locale, item.getValue())
                        : messages.get(locale, item.getLabelKey());
        boolean disabled = dictionary.getStatus() != DictionaryStatus.ACTIVE
                || !item.isSelectable()
                || item.getStatus() != DictionaryStatus.ACTIVE;
        return new DictionaryOption(item.getValue(), item.getLabelKey(), label, item.getTone(), disabled);
    }

    /**
     * 把字典类型持久化数据转换为当前请求语言的管理结果。
     *
     * @param entity 已由 Repository 限定到精确管理范围的字典类型
     * @return 保留原始多语言内容、排序和版本的本地化结果
     */
    private @NotNull ManagedDictionary managedType(@NotNull SysDictionaryTypeEntity entity) {
        Locale locale = LocaleContextHolder.getLocale();
        var labels = labelCodec.decode(entity.getNameLabels());
        String configuredLabel = labels.find(locale);
        String label = configuredLabel != null
                ? configuredLabel
                : entity.getNameKey() == null
                        ? labels.resolve(locale, entity.getCode())
                        : messages.get(locale, entity.getNameKey());
        return new ManagedDictionary(
                entity.getId(),
                entity.getTenantId(),
                entity.getCode(),
                entity.getKind(),
                entity.getSource(),
                entity.getNameKey(),
                labels.values(),
                label,
                entity.getStatus(),
                entity.getSortOrder(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /**
     * 锁定并读取一个即将发生内容或子项变更的字典类型。
     *
     * <p>调用方法必须处于事务中；父行锁保持到事务结束，使规则检查、引用检查和最终写入读取到同一个父字典状态。
     *
     * @param tenantId 精确所属租户；空表示平台全局
     * @param dictionaryId 字典类型主键
     * @return 已锁定并转换为当前请求语言的管理结果
     */
    private @NotNull ManagedDictionary requireManagedTypeForUpdate(
            @Nullable Long tenantId, @NotNull Long dictionaryId) {
        SysDictionaryTypeEntity entity = repository.findManagedTypeForUpdate(tenantId, dictionaryId);
        if (entity == null) {
            throw new BizException(SystemErrorCode.DICTIONARY_NOT_FOUND);
        }
        return managedType(entity);
    }

    /**
     * 把字典项持久化数据转换为当前请求语言的管理结果。
     *
     * @param entity 已由 Repository 限定到已校验父字典的字典项
     * @return 保留原始多语言内容、排序和版本的本地化结果
     */
    private @NotNull ManagedDictionaryItem managedItem(@NotNull SysDictionaryItemEntity entity) {
        Locale locale = LocaleContextHolder.getLocale();
        var labels = labelCodec.decode(entity.getLabelLabels());
        String configuredLabel = labels.find(locale);
        String label = configuredLabel != null
                ? configuredLabel
                : entity.getLabelKey() == null
                        ? labels.resolve(locale, entity.getItemValue())
                        : messages.get(locale, entity.getLabelKey());
        return new ManagedDictionaryItem(
                entity.getId(),
                entity.getDictTypeId(),
                entity.getItemValue(),
                entity.getSource(),
                entity.getLabelKey(),
                labels.values(),
                label,
                entity.getTone(),
                Boolean.TRUE.equals(entity.getSelectable()),
                entity.getStatus(),
                entity.getSortOrder(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /**
     * 校验并清理可配置字典的用户维护多语言内容。
     *
     * <p>允许集合完全来自 {@link SupportedLocale}，增加语言时不修改本方法。简体中文是必填最终回退，其他支持语言可选；
     * 内容保持用户原文，不自动翻译。
     *
     * @param labels HTTP 或业务模块调用方提供的语言标签映射
     * @return 顺序固定、无空白值的受支持语言标签
     */
    private @NotNull Map<String, String> checkedLabels(@NotNull Map<String, String> labels) {
        Map<String, String> checked = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            if (!SupportedLocale.supports(entry.getKey())) {
                throw new BizException(SystemErrorCode.DICTIONARY_LABEL_LANGUAGE_UNSUPPORTED);
            }
            if (StringUtils.hasText(entry.getValue())) {
                checked.put(entry.getKey(), entry.getValue().trim());
            }
        }
        for (SupportedLocale supported : SupportedLocale.values()) {
            if (supported.isRequired() && !checked.containsKey(supported.value())) {
                throw new BizException(
                        SystemErrorCode.DICTIONARY_REQUIRED_LABEL_MISSING,
                        supported.description());
            }
        }
        return Map.copyOf(checked);
    }

    /**
     * 读取已完成父范围校验的字典项。
     *
     * @param dictionaryId 父字典主键
     * @param itemId 字典项主键
     * @return 未删除字典项持久化数据
     */
    private @NotNull SysDictionaryItemEntity requireManagedItem(
            @NotNull Long dictionaryId, @NotNull Long itemId) {
        SysDictionaryItemEntity item = repository.findManagedItem(dictionaryId, itemId);
        if (item == null) {
            throw new BizException(SystemErrorCode.DICTIONARY_ITEM_NOT_FOUND);
        }
        return item;
    }

    @Override
    @Transactional
    public void reorderTypes(@Nullable Long tenantId, @NotNull Map<Long, Long> dictionaryVersions) {
        if (tenantId != null) {
            tenantService.lockForWrite(tenantId);
        }
        repository.lockOrdering("types:" + (tenantId == null ? "platform" : tenantId));
        List<SysDictionaryTypeEntity> types = repository.pageManagedTypes(
                tenantId, null, null, null, 1, Integer.MAX_VALUE).getRecords();
        Map<Long, SysDictionaryTypeEntity> byId = types.stream()
                .collect(Collectors.toMap(SysDictionaryTypeEntity::getId, type -> type));
        if (dictionaryVersions.size() != types.size() || !dictionaryVersions.keySet().equals(byId.keySet())) {
            throw new ConcurrencyConflictException(
                    SystemErrorCode.DICTIONARY_SORT_CHANGED, SystemClientStateTargets.DICTIONARIES);
        }
        List<DictionaryOrder> orders = new ArrayList<>(dictionaryVersions.size());
        int sortOrder = 10;
        for (Map.Entry<Long, Long> entry : dictionaryVersions.entrySet()) {
            SysDictionaryTypeEntity type = byId.get(entry.getKey());
            if (type == null || !entry.getValue().equals(type.getVersion())) {
                throw new ConcurrencyConflictException(
                        SystemErrorCode.DICTIONARY_SORT_CHANGED, SystemClientStateTargets.DICTIONARIES);
            }
            orders.add(new DictionaryOrder(type.getId(), type.getVersion(), sortOrder));
            sortOrder += 10;
        }
        repository.reorderTypes(tenantId, orders);
        log.info("字典类型排序已写入事务 | tenantId={} itemCount={}", tenantId, orders.size());
    }

    @Override
    @Transactional
    public void reorderItems(
            @Nullable Long tenantId,
            @NotNull Long dictionaryId,
            @NotNull Map<Long, Long> itemVersions) {
        if (tenantId != null) {
            tenantService.lockForWrite(tenantId);
        }
        requireManagedType(tenantId, dictionaryId);
        repository.lockOrdering("items:" + dictionaryId);
        List<SysDictionaryItemEntity> items = repository.pageManagedItems(
                dictionaryId, null, null, 1, Integer.MAX_VALUE).getRecords();
        Map<Long, SysDictionaryItemEntity> byId = items.stream()
                .collect(Collectors.toMap(SysDictionaryItemEntity::getId, item -> item));
        if (itemVersions.size() != items.size() || !itemVersions.keySet().equals(byId.keySet())) {
            throw new ConcurrencyConflictException(
                    SystemErrorCode.DICTIONARY_ITEM_SORT_CHANGED, SystemClientStateTargets.DICTIONARIES);
        }
        List<DictionaryOrder> orders = new ArrayList<>(itemVersions.size());
        int sortOrder = 10;
        for (Map.Entry<Long, Long> entry : itemVersions.entrySet()) {
            SysDictionaryItemEntity item = byId.get(entry.getKey());
            if (item == null || !entry.getValue().equals(item.getVersion())) {
                throw new ConcurrencyConflictException(
                        SystemErrorCode.DICTIONARY_ITEM_SORT_CHANGED, SystemClientStateTargets.DICTIONARIES);
            }
            orders.add(new DictionaryOrder(item.getId(), item.getVersion(), sortOrder));
            sortOrder += 10;
        }
        repository.reorderItems(tenantId, dictionaryId, orders);
        log.info(
                "字典项排序已写入事务 | dictionaryId={} tenantId={} itemCount={}",
                dictionaryId,
                tenantId,
                orders.size());
    }
}
