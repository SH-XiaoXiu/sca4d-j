package com.sca4d.core.api.permission;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.resource.Resource;

/**
 * 使用当前调用绑定的 Session 静态资源判断固定操作（Session Operation Permission Policy）。
 *
 * <p>本策略只认领操作资源（Operation，{@link Operation}）。业务资源由所属模块提供独立的 {@link PermissionPolicy}，以便结合业务授权
 * 数据返回允许、拒绝或资源范围（Resource Scope，{@link ResourceScope}）。
 */
public class SessionOperationPermissionPolicy implements PermissionPolicy {

    /** 创建使用当前 Session 静态资源判断固定操作的权限策略。 */
    public SessionOperationPermissionPolicy() {
    }

    /**
     * 判断指定资源是否为固定操作。
     *
     * @param resource 待判断资源
     * @return 资源为 {@link Operation} 时返回 {@code true}
     */
    @Override
    public boolean supports(Resource resource) {
        return resource instanceof Operation;
    }

    /**
     * 根据当前主体绑定的静态资源判断固定操作权限。
     *
     * @param call 已完成主体选择和权限装载的调用
     * @param permission 待判断的固定操作权限
     * @return 当前 Principal 的 Session Resource 包含指定操作时允许，否则拒绝
     * @throws IllegalStateException 传入非操作资源或非 INVOKE 动作时抛出
     */
    @Override
    public PermissionDecision decide(Call call, Permission permission) {
        if (!(permission.resource() instanceof Operation)) {
            throw new IllegalStateException("固定操作权限策略不能判断业务资源");
        }
        if (!Action.INVOKE.equals(permission.action())) {
            throw new IllegalStateException("固定操作资源只支持 INVOKE 动作");
        }
        return call.grants(permission.resource())
                ? PermissionDecision.allow()
                : PermissionDecision.deny("当前业务主体没有目标操作权限");
    }
}
