package com.sca4d.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.access.AccessAction;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.TenantAccessGrantEntity;
import com.sca4d.mapper.TenantAccessMapper;
import com.sca4d.tenant.policy.TenantAccessGrant;
import com.sca4d.tenant.repository.TenantAccessRepository;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;

/** 验证租户 ACL 的读取、替换和删除。 */
class TenantAccessRepositoryTest {

    @Test
    void shouldReadOneOrderedSnapshotAndConvertPersistenceRows() {
        TenantAccessMapper mapper = mock(TenantAccessMapper.class);
        when(mapper.selectListByQueryAs(any(QueryWrapper.class), eq(TenantAccessGrantEntity.class)))
                .thenReturn(List.of(
                        new TenantAccessGrantEntity().setTenantId(null).setAccessAction(AccessAction.WRITE),
                        new TenantAccessGrantEntity().setTenantId(2L).setAccessAction(AccessAction.READ)));
        TenantAccessRepository repository = new TenantAccessRepository(mapper, mock(IdGenerator.class));

        assertThat(repository.findGrants(10L))
                .extracting(TenantAccessGrant::getTenantId, TenantAccessGrant::getAccessAction)
                .containsExactly(
                        Tuple.tuple(null, AccessAction.WRITE),
                        Tuple.tuple(2L, AccessAction.READ));

        ArgumentCaptor<QueryWrapper> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectListByQueryAs(query.capture(), ArgumentMatchers.eq(TenantAccessGrantEntity.class));
    }

    @Test
    void shouldReplaceSnapshotWithOneDeleteAndOneBatchInsert() {
        TenantAccessMapper mapper = mock(TenantAccessMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(101L, 102L, 103L);
        when(mapper.insertGrants(any())).thenReturn(3);
        TenantAccessRepository repository = new TenantAccessRepository(mapper, idGenerator);

        repository.replace(
                10L,
                AccessAction.WRITE,
                List.of(
                        new TenantAccessGrant(1L, AccessAction.READ),
                        new TenantAccessGrant(3L, AccessAction.MANAGE)));

        ArgumentCaptor<QueryWrapper> delete = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).deleteByQuery(delete.capture());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TenantAccessGrantEntity>> inserted = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertGrants(inserted.capture());
        assertThat(inserted.getValue())
                .extracting(
                        TenantAccessGrantEntity::getId,
                        TenantAccessGrantEntity::getPrincipalId,
                        TenantAccessGrantEntity::getTenantId,
                        TenantAccessGrantEntity::getAccessAction)
                .containsExactly(
                        Tuple.tuple(101L, 10L, null, AccessAction.WRITE),
                        Tuple.tuple(102L, 10L, 1L, AccessAction.READ),
                        Tuple.tuple(103L, 10L, 3L, AccessAction.MANAGE));
    }

    @Test
    void shouldKeepEmptyReplacementAndCleanupIdempotent() {
        TenantAccessMapper mapper = mock(TenantAccessMapper.class);
        TenantAccessRepository repository = new TenantAccessRepository(mapper, mock(IdGenerator.class));

        repository.replace(10L, null, List.of());
        repository.replace(10L, null, List.of());
        repository.deleteByPrincipalIds(List.of());
        repository.deleteByTenant(20L);
        repository.deleteByTenant(20L);

        verify(mapper, times(4)).deleteByQuery(any(QueryWrapper.class));
        verify(mapper, never()).insertGrants(any());
    }

    @Test
    void shouldRejectPartialBatchWrite() {
        TenantAccessMapper mapper = mock(TenantAccessMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn(101L);
        when(mapper.insertGrants(any())).thenReturn(0);
        TenantAccessRepository repository = new TenantAccessRepository(mapper, idGenerator);

        assertThatThrownBy(() -> repository.replace(10L, AccessAction.READ, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("租户访问规则写入行数异常");
    }
}
