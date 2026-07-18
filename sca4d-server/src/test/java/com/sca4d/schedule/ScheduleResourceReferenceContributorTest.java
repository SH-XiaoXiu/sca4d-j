package com.sca4d.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReferenceContext;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.entity.ScheduleDefinitionEntity;
import com.sca4d.entity.ScheduleExecutionEntity;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.schedule.repository.ScheduleRepository;
import com.sca4d.schedule.resource.ScheduleResourceReferenceContributor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** 验证 Schedule 资源名称、存在状态和详情页导航地址解析。 */
class ScheduleResourceReferenceContributorTest {

    @Test
    void shouldResolveSchedulesAndExecutionsWithTwoFixedBatchQueries() {
        ScheduleRepository repository = mock(ScheduleRepository.class);
        ScheduleDefinitionEntity schedule = new ScheduleDefinitionEntity()
                .setId(10L)
                .setName("月度通知")
                .setDeleted(0);
        ScheduleExecutionEntity execution = new ScheduleExecutionEntity()
                .setExecutionId(20L)
                .setScheduleId(10L);
        when(repository.findExecutionReferences(Set.of(20L))).thenReturn(List.of(execution));
        when(repository.findReferences(Set.of(10L))).thenReturn(List.of(schedule));
        ScheduleResourceReferenceContributor contributor = new ScheduleResourceReferenceContributor(
                repository, LocalizedMessagesFixture.create());
        ResourceReferenceKey scheduleKey = new ResourceReferenceKey(ScheduleResourceTypes.SCHEDULE, "10");
        ResourceReferenceKey executionKey = new ResourceReferenceKey(ScheduleResourceTypes.SCHEDULE_EXECUTION, "20");

        var references = contributor.resolve(
                Set.of(scheduleKey, executionKey),
                new ResourceReferenceContext(
                        PrincipalType.PLATFORM, 100L, null, Set.of(PlatformPermissions.SCHEDULES_READ)));

        assertThat(references.get(scheduleKey).getLabel()).isEqualTo("月度通知");
        assertThat(references.get(scheduleKey).getPath()).isEqualTo("/platform/schedules/10");
        assertThat(references.get(executionKey).getLabel()).isEqualTo("月度通知 · 执行记录");
        assertThat(references.get(executionKey).getPath())
                .isEqualTo("/platform/schedules/10?executionId=20");
        verify(repository).findExecutionReferences(Set.of(20L));
        verify(repository).findReferences(Set.of(10L));
    }

    @Test
    void shouldHideBusinessLabelWithoutScheduleReadPermission() {
        ScheduleRepository repository = mock(ScheduleRepository.class);
        ScheduleDefinitionEntity schedule = new ScheduleDefinitionEntity()
                .setId(10L)
                .setName("受限计划")
                .setDeleted(0);
        when(repository.findExecutionReferences(Set.of())).thenReturn(List.of());
        when(repository.findReferences(Set.of(10L))).thenReturn(List.of(schedule));
        ScheduleResourceReferenceContributor contributor = new ScheduleResourceReferenceContributor(
                repository, LocalizedMessagesFixture.create());
        ResourceReferenceKey key = new ResourceReferenceKey(ScheduleResourceTypes.SCHEDULE, "10");

        var reference = contributor.resolve(
                Set.of(key),
                new ResourceReferenceContext(PrincipalType.PLATFORM, 100L, null, Set.of()))
                .get(key);

        assertThat(reference.getLabel()).isNull();
        assertThat(reference.getNavigationState()).isEqualTo(ResourceNavigationState.FORBIDDEN);
        assertThat(reference.getPath()).isNull();
        assertThat(reference.getNavigationMessage()).isEqualTo("当前作用域无权查看详情");
    }
}
