package com.sca4d.tenant.view;

import com.sca4d.iam.principal.PrincipalStatus;
import com.sca4d.tenant.MembershipStatus;
import com.sca4d.tenant.RoleAssignment;
import com.sca4d.tenant.TenantMember;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 租户成员及其角色分配的管理端展示对象。
 */
@Data
@Accessors(chain = true)
public class TenantMemberView {

    private Long membershipId;
    private Long principalId;
    private Long subjectId;
    private Long tenantId;
    private String displayName;
    private MembershipStatus membershipStatus;
    private Long membershipVersion;
    private PrincipalStatus principalStatus;
    private Long principalVersion;
    private List<RoleAssignment> roles;

    /**
     * 将已聚合角色的成员读取对象转换为响应结构。
     *
     * @param member 属于指定租户的成员读取对象
     * @return 包含角色分配的成员展示对象
     */
    public static @NotNull TenantMemberView from(@NotNull TenantMember member) {
        return new TenantMemberView()
                .setMembershipId(member.getMembershipId())
                .setPrincipalId(member.getPrincipalId())
                .setSubjectId(member.getSubjectId())
                .setTenantId(member.getTenantId())
                .setDisplayName(member.getDisplayName())
                .setMembershipStatus(member.getMembershipStatus())
                .setMembershipVersion(member.getMembershipVersion())
                .setPrincipalStatus(member.getPrincipalStatus())
                .setPrincipalVersion(member.getPrincipalVersion())
                .setRoles(List.copyOf(member.getRoles()));
    }
}
