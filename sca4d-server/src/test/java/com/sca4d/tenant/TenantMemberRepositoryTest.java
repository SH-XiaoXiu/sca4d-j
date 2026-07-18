package com.sca4d.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mybatisflex.core.paginate.Page;
import com.sca4d.common.web.PageData;
import com.sca4d.mapper.TenantMemberMapper;
import com.sca4d.tenant.repository.TenantMemberRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证租户成员查询的租户范围、角色归并和 Mapper 读取边界。 */
class TenantMemberRepositoryTest {

    @Test
    void shouldReadTenantMemberPageAndMergeRoleAssignments() {
        TenantMemberMapper mapper = mock(TenantMemberMapper.class);
        TenantMemberRepository repository = repository(mapper);
        Page<TenantMember> mapperPage = new Page<>(3, 20);
        TenantMember memberRow = new TenantMember();
        memberRow.setMembershipId(21L);
        memberRow.setPrincipalId(31L);
        memberRow.setSubjectId(41L);
        memberRow.setTenantId(10L);
        memberRow.setDisplayName("Alice");
        memberRow.setMembershipStatus(MembershipStatus.ACTIVE);
        memberRow.setMembershipVersion(2L);
        memberRow.setPrincipalStatus(com.sca4d.iam.principal.PrincipalStatus.ACTIVE);
        memberRow.setPrincipalVersion(3L);
        mapperPage.setRecords(List.of(memberRow));
        when(mapper.selectPage(any(), eq(10L), eq("Alice"), eq(MembershipStatus.ACTIVE)))
                .thenReturn(mapperPage);
        RoleAssignment assignedRole = new RoleAssignment();
        assignedRole.setPrincipalId(31L);
        assignedRole.setRoleId(50L);
        assignedRole.setRoleCode("TENANT_ADMIN");
        assignedRole.setRoleName("租户管理员");
        when(mapper.selectRoleAssignments(List.of(31L))).thenReturn(List.of(assignedRole));

        PageData<TenantMember> result = repository.page(10L, "Alice", MembershipStatus.ACTIVE, 3, 20);

        verify(mapper).selectPage(
                argThat(page -> page.getPageNumber() == 3L && page.getPageSize() == 20L),
                eq(10L),
                eq("Alice"),
                eq(MembershipStatus.ACTIVE));
        assertThat(result.getRecords()).singleElement().satisfies(member -> {
            assertThat(member.getMembershipId()).isEqualTo(21L);
            assertThat(member.getPrincipalId()).isEqualTo(31L);
            assertThat(member.getSubjectId()).isEqualTo(41L);
            assertThat(member.getTenantId()).isEqualTo(10L);
            assertThat(member.getDisplayName()).isEqualTo("Alice");
            assertThat(member.getRoles()).singleElement().satisfies(role -> {
                assertThat(role.getRoleId()).isEqualTo(50L);
                assertThat(role.getRoleCode()).isEqualTo("TENANT_ADMIN");
            });
        });
    }

    @Test
    void shouldPassSearchToMapperWithoutInputNormalization() {
        TenantMemberMapper mapper = mock(TenantMemberMapper.class);
        TenantMemberRepository repository = repository(mapper);
        Page<TenantMember> mapperPage = new Page<>(1, 20);
        mapperPage.setRecords(List.of());
        when(mapper.selectPage(any(), eq(10L), eq(" Alice "), isNull()))
                .thenReturn(mapperPage);

        repository.page(10L, " Alice ", null, 1, 20);

        verify(mapper).selectPage(any(), eq(10L), eq(" Alice "), isNull());
    }

    @Test
    void shouldKeepMemberLookupTenantScoped() {
        TenantMemberMapper mapper = mock(TenantMemberMapper.class);
        TenantMemberRepository repository = repository(mapper);

        assertThat(repository.find(10L, 31L)).isNull();
        verify(mapper).selectByTenantAndPrincipal(10L, 31L);
    }

    private TenantMemberRepository repository(TenantMemberMapper mapper) {
        return new TenantMemberRepository(mapper);
    }
}
