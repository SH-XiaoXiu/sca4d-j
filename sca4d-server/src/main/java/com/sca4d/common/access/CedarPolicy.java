package com.sca4d.common.access;

import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;

/**
 * 使用独立 Cedar Schema 和 Policy 文件的高级资源策略。
 *
 * <p>普通业务策略使用 {@link Rules}。需要 Cedar 模板、Entity 层级或扩展类型时实现本接口，并通过
 * {@link Policy#cedar(CedarPolicy)} 完成启动期解析、校验和缓存。
 *
 * @param <R> 业务模块传入 Cedar 的资源类型
 */
public interface CedarPolicy<R> {

    /** @return 该策略接受的资源类型 */
    @NotNull
    Class<R> resourceType();

    /**
     * 返回 Cedar Schema 的 classpath 位置。
     *
     * @return 相对于实现类的资源路径，或以 {@code /} 开始的绝对 classpath 路径
     */
    @NotNull
    String schemaResource();

    /**
     * 返回 Cedar Policy 文件的 classpath 位置。
     *
     * <p>每条静态策略必须声明唯一且非空的 {@code @id("...")} 注解。
     *
     * @return 相对于实现类的资源路径，或以 {@code /} 开始的绝对 classpath 路径
     */
    @NotNull
    String policyResource();

    /**
     * 将当前请求和业务资源转换为 Cedar Entity。
     *
     * @param request 已完成 Principal 选择和 RBAC 判断的当前请求
     * @param action 当前资源访问等级
     * @param resource 业务模块已经读取的资源
     * @return Cedar 判断所需的全部请求数据
     */
    @NotNull
    CedarRequest request(
            @NotNull CurrentRequest request,
            @NotNull AccessAction action,
            @NotNull R resource);
}
