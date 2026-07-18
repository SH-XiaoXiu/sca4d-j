package com.sca4d.common.resource;

import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 基于业务模块贡献者注册表的资源引用解析实现。 */
@Component
public class DefaultResourceReferenceResolver implements ResourceReferenceResolver {

    private final Map<String, ResourceReferenceContributor> contributorByType;
    private final LocalizedMessages messages;

    /**
     * 创建并校验资源类型唯一的贡献者索引。
     *
     * @param contributors 当前应用全部业务模块贡献者
     * @param messages 按当前请求语言解析消息键的实现
     */
    public DefaultResourceReferenceResolver(
            @NotNull List<ResourceReferenceContributor> contributors,
            @NotNull LocalizedMessages messages) {
        Map<String, ResourceReferenceContributor> registered = new LinkedHashMap<>();
        for (ResourceReferenceContributor contributor : contributors) {
            for (String resourceType : contributor.resourceTypes()) {
                if (resourceType == null || resourceType.isBlank()) {
                    throw new IllegalStateException("资源引用贡献者注册了空资源类型: " + contributor.getClass().getName());
                }
                if (registered.putIfAbsent(resourceType, contributor) != null) {
                    throw new IllegalStateException("重复资源引用类型: " + resourceType);
                }
            }
        }
        this.contributorByType = Map.copyOf(registered);
        this.messages = messages;
    }

    @Override
    public @NotNull Map<ResourceReferenceKey, ResourceReference> resolve(
            @NotNull Collection<ResourceReferenceKey> keys) {
        Set<ResourceReferenceKey> distinctKeys = new LinkedHashSet<>(keys);
        if (distinctKeys.isEmpty()) {
            return Map.of();
        }
        CurrentRequest request = CurrentRequestHolder.current();
        ResourceReferenceContext context = new ResourceReferenceContext(
                request.requirePrincipal().requirePrincipalType(),
                request.requirePrincipal().requirePrincipalId(),
                request.requirePrincipal().getTenantId(),
                request.requireSessionPermission().getPermissionCodes());
        Map<ResourceReferenceContributor, Set<ResourceReferenceKey>> keysByContributor = new LinkedHashMap<>();
        Map<ResourceReferenceKey, ResourceReference> resolved = new LinkedHashMap<>();
        for (ResourceReferenceKey key : distinctKeys) {
            ResourceReferenceContributor contributor = contributorByType.get(key.getResourceType());
            if (contributor == null) {
                resolved.put(key, unavailable(key, ResourceNavigationState.UNSUPPORTED, "resource.reference.unsupported"));
            } else {
                keysByContributor.computeIfAbsent(contributor, ignored -> new LinkedHashSet<>()).add(key);
            }
        }
        for (Map.Entry<ResourceReferenceContributor, Set<ResourceReferenceKey>> entry : keysByContributor.entrySet()) {
            resolved.putAll(entry.getKey().resolve(Set.copyOf(entry.getValue()), context));
        }
        for (ResourceReferenceKey key : distinctKeys) {
            resolved.putIfAbsent(key, unavailable(key, ResourceNavigationState.DELETED, "resource.reference.deleted"));
        }
        return Map.copyOf(resolved);
    }

    /**
     * 创建不暴露裸 ID 的不可导航引用。
     *
     * @param key 原始资源键
     * @param state 明确不可导航状态
     * @param reasonKey 当前语言目录中的原因键
     * @return 不包含伪造名称或数据库 ID 的不可导航引用
     */
    private @NotNull ResourceReference unavailable(
            @NotNull ResourceReferenceKey key,
            @NotNull ResourceNavigationState state,
            @NotNull String reasonKey) {
        String reason = messages.get(reasonKey);
        return new ResourceReference(key, null, null, state, null, reason);
    }
}
