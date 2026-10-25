package com.sca4d.core.spring.permission;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.ResourceScope;
import com.sca4d.core.api.permission.ResourceScopeRequirement;
import com.sca4d.core.spring.call.CallHolder;

import java.util.Optional;

/** 为数据仓库读取当前调用已经批准的业务资源范围。 */
public class CurrentResourceScope {

    public CurrentResourceScope() {
    }

    /**
     * 取得指定权限已经批准的资源范围。
     *
     * <p>遍历 {@link CallHolder} 中的全部 {@link CallExecution}，把每个匹配的登记标记为已消费，并返回最内层执行中的范围。
     * 此方法不读写数据库，也不修改 Session。
     *
     * @param permission 数据仓库正在执行的完整权限
     * @param scopeType 业务模块定义的资源范围类型
     * @param <S> 资源范围类型
     * @return 当前调用已经批准的资源范围
     * @throws BusinessException 当前调用中没有该权限的资源范围登记时抛出，错误码为 PERMISSION_DENIED
     * @throws IllegalStateException 已登记的资源范围类型与 scopeType 不符时抛出
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
