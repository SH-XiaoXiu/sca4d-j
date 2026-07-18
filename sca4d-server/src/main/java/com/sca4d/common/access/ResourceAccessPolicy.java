package com.sca4d.common.access;

import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 一个业务模块资源的共用访问范围策略。
 *
 * <p>列表、详情和 CUD 使用同一实现。实现先读取 Cedar 判断需要的资源，再生成 Repository 必须消费的
 * Criteria；凭证、Session、RBAC 和 SQL 不由本接口处理。
 *
 * @param <R> Cedar 判断使用的业务资源类型
 * @param <C> 该业务模块固定的访问范围类型
 */
public interface ResourceAccessPolicy<R, C extends AccessCriteria> {

    /**
     * 返回该业务模块使用的 Cedar Policy。
     *
     * @return 启动期已经解析、校验和缓存的 Policy
     */
    @NotNull
    Policy<R> policy();

    /**
     * 读取单次 Cedar 判断需要的资源。
     *
     * <p>集合访问可以返回表示集合的资源。关系数据必须使用固定次数查询，不能逐项读取列表中的资源。
     *
     * @param request 已完成 Principal 选择和 RBAC 判断的当前请求
     * @param action 当前接口声明的资源访问等级
     * @param resourceId 注解 SpEL 解析出的可选资源标识
     * @return 传入 Policy 和 Criteria 生成阶段的业务资源
     */
    @NotNull
    R resource(
            @NotNull CurrentRequest request,
            @NotNull AccessAction action,
            @Nullable Object resourceId);

    /**
     * 返回该策略生成的 Criteria 类型。
     *
     * @return 唯一 Criteria 类型
     */
    @NotNull
    Class<C> criteriaType();

    /**
     * 根据已经通过 Cedar 判断的资源生成 Repository 查询范围。
     *
     * <p>该方法不得重复读取 {@link #resource(CurrentRequest, AccessAction, Object)} 已经取得的关系数据。
     *
     * @param request 当前请求
     * @param action 固定访问等级
     * @param resource 已经通过 Cedar 判断的业务资源
     * @return Repository 必须消费的业务模块 Criteria
     */
    @NotNull
    C criteria(
            @NotNull CurrentRequest request,
            @NotNull AccessAction action,
            @NotNull R resource);
}
