package com.sca4d.common.access;

import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import org.jetbrains.annotations.NotNull;

/** Repository 从当前请求读取业务模块访问范围的唯一入口。 */
public class ResourceAccess {

    /**
     * 获取当前请求已经由 {@code @Access} 解析的业务模块 Criteria。
     *
     * @param type Criteria 固定类型
     * @param <C> Criteria 类型
     * @return 当前请求的访问范围
     */
    public <C extends AccessCriteria> @NotNull C require(@NotNull Class<C> type) {
        CurrentRequest request = CurrentRequestHolder.currentOrNull();
        if (request == null || request.getPrincipal() == null) {
            throw new ResourceAccessDeniedException();
        }
        return request.requireAccess(type);
    }
}
