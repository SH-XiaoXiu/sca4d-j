package com.sca4d.core.spring.permission;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.ResourceScope;
import com.sca4d.core.api.permission.ResourceScopeRequirement;
import com.sca4d.core.spring.call.CallHolder;

import java.util.Optional;

/** 为数据仓库读取当前调用已经批准的业务资源范围。 */
public class CurrentResourceScope {

    /** 创建供 Repository 读取当前资源范围的入口。 */
    public CurrentResourceScope() {
    }

    /**
     * 取得指定权限已经批准的资源范围。
     *
     * @param permission 数据仓库正在执行的完整权限
     * @param scopeType 业务模块定义的资源范围类型
     * @param <S> 资源范围类型
     * @return 当前调用已经批准的资源范围
     */
    public <S extends ResourceScope> S scope(Permission permission, Class<S> scopeType) {
        S nearest = null;
        for (CallExecution execution : CallHolder.executions()) {
            Optional<S> consumed = ResourceScopeRequirement.consume(execution, permission, scopeType);
            if (nearest == null && consumed.isPresent()) {
                nearest = consumed.orElseThrow();
            }
        }
        if (nearest == null) {
            throw new BusinessException(CoreErrorCode.PERMISSION_DENIED);
        }
        return nearest;
    }
}
