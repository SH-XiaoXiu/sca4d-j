package com.sca4d.iam.base.mapper;

import com.sca4d.core.api.permission.Action;
import com.sca4d.iam.acl.AclEntry;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 持久化 Platform ACL 条目。 */
public interface PlatformAclMapper {
    /**
     * 幂等新增 ACL 条目。
     *
     * @param principalId 业务主体标识
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param action 动作值
     * @return 新增行数
     */
    int insert(
            @Param("principalId") @NotNull Long principalId,
            @Param("resourceType") @NotNull String resourceType,
            @Param("resourceId") @NotNull String resourceId,
            @Param("action") @NotNull Action action);

    /**
     * 按主体或完整资源范围删除 ACL 条目；没有任何完整范围时不删除。
     *
     * @param principalId 业务主体标识
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param action 动作值
     * @return 删除行数
     */
    int delete(
            @Param("principalId") @Nullable Long principalId,
            @Param("resourceType") @Nullable String resourceType,
            @Param("resourceId") @Nullable String resourceId,
            @Param("action") @Nullable Action action);

    /**
     * 查询主体在一种资源类型下的 ACL 条目，可按资源和动作过滤并限制返回数量。
     *
     * @param principalId 业务主体标识
     * @param resourceType 资源类型
     * @return ACL 查询结果
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull AclEntry> selectEntries(
            @Param("principalId") @NotNull Long principalId,
            @Param("resourceType") @NotNull String resourceType,
            @Param("resourceId") @Nullable String resourceId,
            @Param("action") @Nullable Action action,
            @Param("limit") @Nullable Integer limit);
}
