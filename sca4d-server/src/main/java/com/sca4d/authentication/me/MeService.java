package com.sca4d.authentication.me;

import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;

/** 查询当前登录 Subject、Principal、角色、权限和租户成员关系。 */
public interface MeService {

    /**
     * 读取当前已认证身份在本次请求中的固定认证快照。
     *
     * <p>快照包含 Subject 对应的自然人资料、已选 Principal 与 Session，不暴露 HTTP、STOMP 载荷、请求参数或
     * 数据访问条件。当前请求尚未认证时抛出共用未认证异常。
     *
     * @param currentRequest 已完成认证和 Principal 选择的共用请求态
     * @return 可直接转换为当前用户响应的认证运行时快照
     */
    @NotNull
    MeSnapshot snapshot(@NotNull CurrentRequest currentRequest);
}
