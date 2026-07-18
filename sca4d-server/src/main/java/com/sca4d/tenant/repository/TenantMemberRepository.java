package com.sca4d.tenant.repository;

import com.mybatisflex.core.paginate.Page;
import com.sca4d.common.web.PageData;
import com.sca4d.mapper.TenantMemberMapper;
import com.sca4d.tenant.MembershipStatus;
import com.sca4d.tenant.RoleAssignment;
import com.sca4d.tenant.TenantMember;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询成员、Subject、Principal 和角色组成的租户成员读模型。
 *
 * <p>成员和角色绑定在这里聚合，调用方不会观察到角色关系表或分页实现细节。分页固定执行 2 条分页
 * SQL，非空页再执行 1 条角色查询；详情最多 2 条 SQL，不随成员数量形成 N+1。
 */
@Repository
@RequiredArgsConstructor
public class TenantMemberRepository {

    private final TenantMemberMapper mapper;

    /**
     * 分页读取租户内可见的成员及其有效角色。
     *
     * <p>成员必须同时具备未删除的 TENANT Principal 和 Subject；搜索覆盖主体名称、身份名称和联系方式。分页查询由成员
     * Mapper 保持 SQL 细节，非空页再用 1 条批量角色查询归并，因此每页总计 2 或 3 条 SQL，不随成员数量增长。
     *
     * @param tenantId 成员所属租户
     * @param search 成员搜索文本
     * @param status 成员关系状态
     * @param page 页码
     * @param size 每页记录数
     * @return 已归并角色的成员分页
     */
    public @NotNull PageData<TenantMember> page(
            @NotNull Long tenantId,
            @Nullable String search,
            @Nullable MembershipStatus status,
            long page,
            long size) {
        Page<TenantMember> memberPage = mapper.selectPage(new Page<>(page, size), tenantId, search, status);
        List<TenantMember> members = memberPage.getRecords();
        appendRoleAssignments(members);
        return new PageData<>(members, memberPage.getPageNumber(), memberPage.getPageSize(), memberPage.getTotalRow());
    }

    /**
     * 读取一个租户成员及其角色。
     *
     * <p>成员不存在时只执行 1 条查询；存在时追加 1 条角色查询，总计最多 2 条 SQL。
     *
     * @param tenantId 租户主键
     * @param principalId 成员 TENANT Principal 主键
     * @return 成员及其有效角色；不存在时为 {@code null}
     */
    public @Nullable TenantMember find(@NotNull Long tenantId, @NotNull Long principalId) {
        TenantMember member = mapper.selectByTenantAndPrincipal(tenantId, principalId);
        if (member == null) {
            return null;
        }
        appendRoleAssignments(List.of(member));
        return member;
    }

    /** 将同页成员与其角色绑定组合为稳定的租户成员读模型。 */
    private void appendRoleAssignments(@NotNull List<TenantMember> members) {
        if (members.isEmpty()) {
            return;
        }
        Map<Long, TenantMember> membersByPrincipal = new LinkedHashMap<>();
        for (TenantMember member : members) {
            membersByPrincipal.put(member.getPrincipalId(), member);
        }
        for (RoleAssignment role : mapper.selectRoleAssignments(List.copyOf(membersByPrincipal.keySet()))) {
            TenantMember member = membersByPrincipal.get(role.getPrincipalId());
            if (member != null) {
                member.getRoles().add(role);
            }
        }
    }

}
