package com.sca4d.tenant;

import com.sca4d.common.web.PageData;
import com.sca4d.iam.tenant.TenantStatus;
import com.sca4d.iam.tenant.TenantType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 创建和查询租户，并修改租户状态与成员所属租户。
 */
public interface TenantService {

    /**
     * 分页查询符合筛选条件的租户。
     *
     * @param search 已转换的关键词；缺失时不按关键词过滤
     * @param status 目标租户状态；缺失时不按状态过滤
     * @param page 页码，从 1 开始
     * @param size 每页记录数
     * @return 租户分页结果
     */
    @NotNull
    PageData<Tenant> page(@Nullable String search, @Nullable TenantStatus status, long page, long size);

    /**
     * 读取一个仍存在的租户。
     *
     * @param tenantId 租户主键
     * @return 租户读取对象
     * @throws com.sca4d.common.exception.BizException 租户不存在时抛出
     */
    @NotNull
    Tenant require(@NotNull Long tenantId);

    /**
     * 读取一个允许成员进入的活跃租户。
     *
     * @param tenantId 租户主键
     * @return 活跃租户读取对象
     * @throws com.sca4d.common.exception.BizException 租户不存在或已停用时抛出
     */
    @NotNull
    Tenant requireActive(@NotNull Long tenantId);

    /**
     * 使用当前租户业务编码读取允许登录的活跃租户。
     *
     * @param tenantCode 当前租户业务编码
     * @return 活跃租户读取对象
     * @throws com.sca4d.common.exception.BizException 编码无效、租户不存在或已停用时抛出
     */
    @NotNull
    Tenant requireActiveByCode(@NotNull String tenantCode);

    /**
     * 在当前写事务中锁定一个仍存在的租户。
     *
     * @param tenantId 租户主键
     * @return 锁定时的租户状态
     */
    @NotNull
    TenantStatus lockForWrite(@NotNull Long tenantId);

    /**
     * 在当前写事务中锁定一个允许接纳成员的活跃租户。
     *
     * @param tenantId 租户主键
     */
    void lockActiveForWrite(@NotNull Long tenantId);

    /**
     * 创建租户并永久登记首次使用的业务编码。
     *
     * @param code 租户业务编码
     * @param name 租户展示名称
     * @param tenantType 租户类型
     * @return 新建租户主键
     */
    @NotNull
    Long create(@NotNull String code, @NotNull String name, @NotNull TenantType tenantType);

    /**
     * 更新租户资料。
     *
     * @param tenantId 租户主键
     * @param code 新的租户业务编码；修改后旧编码仍永久占用
     * @param name 当前资料契约提供的新名称
     * @param version 调用方读取到的乐观锁版本
     */
    void update(
            @NotNull Long tenantId,
            @NotNull String code,
            @NotNull String name,
            @NotNull Long version);

    /**
     * 变更租户自身状态，不批量修改成员 Principal。
     *
     * @param tenantId 租户主键
     * @param status 目标租户状态
     * @param version 调用方读取到的乐观锁版本
     */
    void changeStatus(@NotNull Long tenantId, @NotNull TenantStatus status, @NotNull Long version);

    /**
     * 将租户推进到不可恢复删除终态，并登记异步关系清理任务。
     *
     * @param tenantId 租户主键
     * @param version 调用方读取到的乐观锁版本
     */
    void delete(@NotNull Long tenantId, @NotNull Long version);

    /**
     * 查询一个 Subject 可进入的租户成员关系。
     *
     * @param subjectId Subject 主键
     * @return 按租户隔离的成员关系
     */
    @NotNull
    List<TenantMembership> memberships(@NotNull Long subjectId);
}
