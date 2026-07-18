package com.sca4d.mail.resource;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReference;
import com.sca4d.common.resource.ResourceReferenceContext;
import com.sca4d.common.resource.ResourceReferenceContributor;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.entity.SysMailSenderEntity;
import com.sca4d.mail.MailResourceTypes;
import com.sca4d.mail.repository.MailSenderRepository;
import com.sca4d.system.SystemPermissions;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 批量解析操作日志中的 SMTP 配置名称、存在状态和详情页地址。 */
@Component
@RequiredArgsConstructor
public class MailSenderResourceReferenceContributor implements ResourceReferenceContributor {

    private static final Set<String> RESOURCE_TYPES = Set.of(MailResourceTypes.MAIL_SENDER);

    private final MailSenderRepository repository;
    private final LocalizedMessages messages;

    @Override
    public @NotNull Set<String> resourceTypes() {
        return RESOURCE_TYPES;
    }

    @Override
    public @NotNull Map<ResourceReferenceKey, ResourceReference> resolve(
            @NotNull Set<ResourceReferenceKey> keys, @NotNull ResourceReferenceContext context) {
        Set<Long> senderIds = new LinkedHashSet<>();
        for (ResourceReferenceKey key : keys) {
            try {
                senderIds.add(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                // 非法历史 ID 不进入 SQL，下面按删除态返回且不回显裸值。
            }
        }
        Map<Long, SysMailSenderEntity> senders = repository.findSenderReferences(senderIds).stream()
                .collect(Collectors.toMap(SysMailSenderEntity::getId, Function.identity()));
        Map<ResourceReferenceKey, ResourceReference> references = new LinkedHashMap<>();
        for (ResourceReferenceKey key : keys) {
            SysMailSenderEntity sender;
            try {
                sender = senders.get(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                sender = null;
            }
            if (sender == null || sender.getDeleted() == null || sender.getDeleted() != 0) {
                references.put(key, new ResourceReference(
                        key,
                        null,
                        null,
                        ResourceNavigationState.DELETED,
                        null,
                        messages.get("resource.reference.deleted")));
            } else if (context.getPrincipalType() != PrincipalType.PLATFORM
                    || !context.hasPermission(SystemPermissions.MAIL_SENDERS_READ)) {
                references.put(key, new ResourceReference(
                        key,
                        null,
                        null,
                        ResourceNavigationState.FORBIDDEN,
                        null,
                        messages.get("resource.reference.forbidden")));
            } else {
                references.put(key, new ResourceReference(
                        key,
                        sender.getName(),
                        null,
                        ResourceNavigationState.NAVIGABLE,
                        "/system/mail-senders/" + sender.getId(),
                        null));
            }
        }
        return Map.copyOf(references);
    }
}
