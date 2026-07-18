package com.sca4d.common.auth;

import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 根据当前 Subject 和 Session 选择本次请求使用的 Principal。
 *
 * <p>业务模块实现本接口后，可以把当前 Subject 和 Session 转换成本次请求使用的 Principal。
 */
public interface PrincipalSelection<P> {

    /**
     * 获取额外选择参数类型。
     *
     * @return 参数类型
     */
    @NotNull
    Class<P> parameterType();

    /**
     * 选择当前请求执行主体。
     *
     * @param request 当前请求认证授权快照
     * @param allowedTypes 接口允许的主体类型；为空时由实现选择默认主体类型
     * @param principalId 显式指定的 Principal ID
     * @param tenantId 显式指定的 Tenant ID
     * @param selectionKey 业务选择键
     * @param parameters 额外命名选择参数
     * @return 当前执行主体
     */
    @NotNull
    Principal select(
            @NotNull CurrentRequest request,
            @NotNull PrincipalType[] allowedTypes,
            @Nullable Long principalId,
            @Nullable Long tenantId,
            @Nullable String selectionKey,
            @NotNull P parameters);

    /**
     * 获取默认 Map 参数类型。
     *
     * @return Map 参数类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static @NotNull Class<Map<String, Object>> mapParameterType() {
        return (Class) Map.class;
    }
}
