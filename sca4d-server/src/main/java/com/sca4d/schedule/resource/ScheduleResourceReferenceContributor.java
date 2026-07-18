package com.sca4d.schedule.resource;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReference;
import com.sca4d.common.resource.ResourceReferenceContext;
import com.sca4d.common.resource.ResourceReferenceContributor;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.entity.ScheduleDefinitionEntity;
import com.sca4d.entity.ScheduleExecutionEntity;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.schedule.ScheduleResourceTypes;
import com.sca4d.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Schedule 定义和执行数据的批量资源引用贡献者。 */
@Component
@RequiredArgsConstructor
public class ScheduleResourceReferenceContributor implements ResourceReferenceContributor {

    private final ScheduleRepository repository;
    private final LocalizedMessages messages;

    @Override
    public @NotNull Set<String> resourceTypes() {
        return Set.of(ScheduleResourceTypes.SCHEDULE, ScheduleResourceTypes.SCHEDULE_EXECUTION);
    }

    @Override
    public @NotNull Map<ResourceReferenceKey, ResourceReference> resolve(
            @NotNull Set<ResourceReferenceKey> keys, @NotNull ResourceReferenceContext context) {
        Set<ResourceReferenceKey> scheduleKeys = new LinkedHashSet<>();
        Set<ResourceReferenceKey> executionKeys = new LinkedHashSet<>();
        Set<Long> scheduleIds = new LinkedHashSet<>();
        Set<Long> executionIds = new LinkedHashSet<>();
        for (ResourceReferenceKey key : keys) {
            Set<ResourceReferenceKey> typedKeys;
            Set<Long> typedIds;
            if (ScheduleResourceTypes.SCHEDULE.equals(key.getResourceType())) {
                typedKeys = scheduleKeys;
                typedIds = scheduleIds;
            } else if (ScheduleResourceTypes.SCHEDULE_EXECUTION.equals(key.getResourceType())) {
                typedKeys = executionKeys;
                typedIds = executionIds;
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
        Map<Long, ScheduleExecutionEntity> executions = repository.findExecutionReferences(Set.copyOf(executionIds)).stream()
                .collect(Collectors.toMap(ScheduleExecutionEntity::getExecutionId, Function.identity()));
        executions.values().stream()
                .map(ScheduleExecutionEntity::getScheduleId)
                .filter(Objects::nonNull)
                .forEach(scheduleIds::add);
        Map<Long, ScheduleDefinitionEntity> schedules = repository.findReferences(Set.copyOf(scheduleIds)).stream()
                .collect(Collectors.toMap(ScheduleDefinitionEntity::getId, Function.identity()));
        boolean allowed = context.getPrincipalType() == PrincipalType.PLATFORM
                && context.hasPermission(PlatformPermissions.SCHEDULES_READ);
        String deletedReason = messages.get("resource.reference.deleted");
        String forbiddenReason = messages.get("resource.reference.forbidden");
        String defaultScheduleLabel = messages.get("resource.reference.schedule");

        Map<ResourceReferenceKey, ResourceReference> references = new LinkedHashMap<>();
        for (ResourceReferenceKey key : scheduleKeys) {
            ScheduleDefinitionEntity schedule;
            try {
                schedule = schedules.get(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                schedule = null;
            }
            if (schedule == null || schedule.getDeleted() == null || schedule.getDeleted() != 0) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.DELETED, null, deletedReason));
                continue;
            }
            String label = schedule.getName() == null || schedule.getName().isBlank()
                    ? defaultScheduleLabel
                    : schedule.getName();
            references.put(key, allowed
                    ? new ResourceReference(
                            key,
                            label,
                            null,
                            ResourceNavigationState.NAVIGABLE,
                            "/platform/schedules/" + schedule.getId(),
                            null)
                    : new ResourceReference(
                            key, null, null, ResourceNavigationState.FORBIDDEN, null, forbiddenReason));
        }
        for (ResourceReferenceKey key : executionKeys) {
            ScheduleExecutionEntity execution;
            try {
                execution = executions.get(Long.valueOf(key.getResourceId()));
            } catch (NumberFormatException ignored) {
                execution = null;
            }
            ScheduleDefinitionEntity schedule = execution == null ? null : schedules.get(execution.getScheduleId());
            if (execution == null || schedule == null
                    || schedule.getDeleted() == null || schedule.getDeleted() != 0) {
                references.put(key, new ResourceReference(
                        key, null, null, ResourceNavigationState.DELETED, null, deletedReason));
                continue;
            }
            String scheduleName = schedule.getName() == null || schedule.getName().isBlank()
                    ? defaultScheduleLabel
                    : schedule.getName();
            String label = messages.get("resource.reference.schedule-execution", scheduleName);
            references.put(key, allowed
                    ? new ResourceReference(
                            key,
                            label,
                            null,
                            ResourceNavigationState.NAVIGABLE,
                            "/platform/schedules/" + schedule.getId() + "?executionId=" + execution.getExecutionId(),
                            null)
                    : new ResourceReference(
                            key, null, null, ResourceNavigationState.FORBIDDEN, null, forbiddenReason));
        }
        return Map.copyOf(references);
    }
}
