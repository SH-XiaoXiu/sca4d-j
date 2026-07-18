package com.sca4d.mapper;

import com.mybatisflex.core.paginate.Page;
import com.sca4d.tenant.MembershipStatus;
import com.sca4d.tenant.RoleAssignment;
import com.sca4d.tenant.TenantMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 租户成员读模型的 SQL Mapper。
 *
 * <p>该 Mapper 只读取租户成员及其角色绑定，不修改 Principal 状态或角色关系。
 */
@Mapper
public interface TenantMemberMapper {

    /**
     * 分页查询一个租户内未删除成员的基础读模型。
     *
     * @param page MyBatis-Flex 分页容器
     * @param tenantId 租户主键
     * @param search 已规范化的成员搜索词；空值时不筛选
     * @param status 成员关系状态；空值时不筛选
     * @return 不含角色分配的成员分页
     */
    @NotNull
    Page<TenantMember> selectPage(
            @NotNull Page<TenantMember> page,
            @Param("tenantId") @NotNull Long tenantId,
            @Param("search") @Nullable String search,
            @Param("status") @Nullable MembershipStatus status);

    /**
     * 查询一个租户成员基础读模型。
     *
     * @param tenantId 租户主键
     * @param principalId 成员 TENANT Principal 主键
     * @return 匹配成员；不存在时为 {@code null}
     */
    @Nullable
    TenantMember selectByTenantAndPrincipal(
            @Param("tenantId") @NotNull Long tenantId,
            @Param("principalId") @NotNull Long principalId);

    /**
     * 查询一组 Principal 的有效角色绑定。
     *
     * @param principalIds 需要加载角色的 Principal 主键集合
     * @return 按 Principal 和角色编码排序的角色绑定
     */
    @NotNull
    List<RoleAssignment> selectRoleAssignments(@Param("principalIds") @NotNull List<Long> principalIds);

}
