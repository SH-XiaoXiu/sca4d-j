package com.sca4d.core.api.permission;

import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.resource.Resource;

/**
 * 对自己认领的资源作出权限判定的策略（Permission Policy）。
 *
 * <p>策略负责完整判断当前业务主体能否执行目标权限。集合资源被部分允许时，策略通过 {@link PermissionDecision} 返回所属业务模块定义的
 * {@link ResourceScope}。
 */
public interface PermissionPolicy {

    /**
     * 判断当前策略是否认领指定资源。
     *
     * @param resource 待判断资源
     * @return 当前策略负责该资源时返回 {@code true}
     */
    boolean supports(Resource resource);

    /**
     * 对已认领资源作出权限判定。
     *
     * @param call 已完成身份和权限装载的调用
     * @param permission 待判断权限
     * @return 允许、拒绝或携带资源范围的允许判定
     */
    PermissionDecision decide(Call call, Permission permission);
}
