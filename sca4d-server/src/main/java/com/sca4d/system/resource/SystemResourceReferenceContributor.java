package com.sca4d.system.resource;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReference;
import com.sca4d.common.resource.ResourceReferenceContext;
import com.sca4d.common.resource.ResourceReferenceContributor;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.entity.SysDictionaryItemEntity;
import com.sca4d.entity.SysDictionaryTypeEntity;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.system.DictionaryLabelCodec;
import com.sca4d.system.SystemPermissions;
import com.sca4d.system.SystemResourceTypes;
import com.sca4d.system.repository.DictionaryRepository;
import com.sca4d.tenant.TenantPermissions;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 系统字典类型和字典项的批量资源引用贡献者。 */
@Component
@RequiredArgsConstructor
public class SystemResourceReferenceContributor implements ResourceReferenceContributor {

    private static final Set<String> RESOURCE_TYPES = Set.of(SystemResourceTypes.DICTIONARY, SystemResourceTypes.DICTIONARY_ITEM);

    private final DictionaryRepository repository;
    private final DictionaryLabelCodec labelCodec;
    private final LocalizedMessages messages;

    @Override
    public @NotNull Set<String> resourceTypes() {
        return RESOURCE_TYPES;
    }

    @Override
    public @NotNull Map<ResourceReferenceKey, ResourceReference> resolve(
            @NotNull Set<ResourceReferenceKey> keys, @NotNull ResourceReferenceContext context) {
        Set<ResourceReferenceKey> typeKeys = new LinkedHashSet<>();
        Set<ResourceReferenceKey> itemKeys = new LinkedHashSet<>();
        Set<Long> typeIds = new LinkedHashSet<>();
        Set<Long> itemIds = new LinkedHashSet<>();
        for (ResourceReferenceKey key : keys) {
            Set<ResourceReferenceKey> typedKeys;
            Set<Long> typedIds;
            if (SystemResourceTypes.DICTIONARY.equals(key.getResourceType())) {
                typedKeys = typeKeys;
                typedIds = typeIds;
            } else if (SystemResourceTypes.DICTIONARY_ITEM.equals(key.getResourceType())) {
                typedKeys = itemKeys;
                typedIds = itemIds;
            } else {
                continue;
            }
            typedKeys.add(key);
            try {
                typedIds.add(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                // 非数字历史主键保留在结果中，并在下方返回删除态。
            }
        }
        Map<Long, SysDictionaryItemEntity> items = repository.findItemReferences(Set.copyOf(itemIds)).stream()
                .collect(Collectors.toMap(SysDictionaryItemEntity::getId, Function.identity()));
        items.values().stream()
                .map(SysDictionaryItemEntity::getDictTypeId)
                .filter(Objects::nonNull)
                .forEach(typeIds::add);
        Map<Long, SysDictionaryTypeEntity> types = repository.findTypeReferences(Set.copyOf(typeIds)).stream()
                .collect(Collectors.toMap(SysDictionaryTypeEntity::getId, Function.identity()));
        String deletedReason = messages.get("resource.reference.deleted");
        String forbiddenReason = messages.get("resource.reference.forbidden");

        Map<ResourceReferenceKey, ResourceReference> references = new LinkedHashMap<>();
        for (ResourceReferenceKey key : typeKeys) {
            SysDictionaryTypeEntity type;
            try {
                type = types.get(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                type = null;
            }
            if (type == null || type.getDeleted() == null || type.getDeleted() != 0) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.DELETED, null, deletedReason));
                continue;
            }
            String label = type.getNameKey() == null
                    ? labelCodec.decode(type.getNameLabels()).resolve(LocaleContextHolder.getLocale(), type.getCode())
                    : messages.get(LocaleContextHolder.getLocale(), type.getNameKey());
            String path = null;
            if (type.getTenantId() == null
                    && context.getPrincipalType() == PrincipalType.PLATFORM
                    && context.hasPermission(SystemPermissions.DICTIONARIES_READ)) {
                path = "/system/dictionaries/" + type.getId();
            } else if (type.getTenantId() != null
                    && context.getPrincipalType() == PrincipalType.PLATFORM
                    && context.hasPermission(PlatformPermissions.TENANT_DICTIONARIES_READ)) {
                path = "/platform/tenants/" + type.getTenantId() + "/dictionaries/" + type.getId();
            } else if (type.getTenantId() != null
                    && type.getTenantId().equals(context.getTenantId())
                    && context.getPrincipalType() == PrincipalType.TENANT
                    && context.hasPermission(TenantPermissions.TENANT_DICTIONARIES_READ)) {
                path = "/tenant/tenants/" + type.getTenantId() + "/dictionaries/" + type.getId();
            }
            references.put(key, path == null
                    ? new ResourceReference(
                            key, null, type.getTenantId(), ResourceNavigationState.FORBIDDEN, null, forbiddenReason)
                    : new ResourceReference(
                            key, label, type.getTenantId(), ResourceNavigationState.NAVIGABLE, path, null));
        }
        for (ResourceReferenceKey key : itemKeys) {
            SysDictionaryItemEntity item;
            try {
                item = items.get(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                item = null;
            }
            SysDictionaryTypeEntity type = item == null ? null : types.get(item.getDictTypeId());
            if (item == null || item.getDeleted() == null || item.getDeleted() != 0
                    || type == null || type.getDeleted() == null || type.getDeleted() != 0) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.DELETED, null, deletedReason));
                continue;
            }
            String label = item.getLabelKey() == null
                    ? labelCodec.decode(item.getLabelLabels()).resolve(LocaleContextHolder.getLocale(), item.getItemValue())
                    : messages.get(LocaleContextHolder.getLocale(), item.getLabelKey());
            String itemQuery = "?itemId=" + item.getId();
            String path = null;
            if (type.getTenantId() == null
                    && context.getPrincipalType() == PrincipalType.PLATFORM
                    && context.hasPermission(SystemPermissions.DICTIONARIES_READ)) {
                path = "/system/dictionaries/" + type.getId() + itemQuery;
            } else if (type.getTenantId() != null
                    && context.getPrincipalType() == PrincipalType.PLATFORM
                    && context.hasPermission(PlatformPermissions.TENANT_DICTIONARIES_READ)) {
                path = "/platform/tenants/" + type.getTenantId() + "/dictionaries/" + type.getId() + itemQuery;
            } else if (type.getTenantId() != null
                    && type.getTenantId().equals(context.getTenantId())
                    && context.getPrincipalType() == PrincipalType.TENANT
                    && context.hasPermission(TenantPermissions.TENANT_DICTIONARIES_READ)) {
                path = "/tenant/tenants/" + type.getTenantId() + "/dictionaries/" + type.getId() + itemQuery;
            }
            references.put(key, path == null
                    ? new ResourceReference(
                            key, null, type.getTenantId(), ResourceNavigationState.FORBIDDEN, null, forbiddenReason)
                    : new ResourceReference(
                            key, label, type.getTenantId(), ResourceNavigationState.NAVIGABLE, path, null));
        }
        return Map.copyOf(references);
    }
}
