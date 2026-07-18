package com.sca4d.common.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalSelection;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Principal 选择调用器。
 *
 * <p>负责把适配层解析出的 Map 参数转换成 {@link PrincipalSelection} 声明的参数类型。
 */
public class PrincipalSelectionInvoker {

    private final ObjectMapper objectMapper;

    /**
     * 创建 Principal 选择调用器。
     *
     * @param objectMapper JSON 对象映射器
     */
    public PrincipalSelectionInvoker(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 调用指定的 PrincipalSelection 实现选择当前 Principal。
     *
     * @param selection 本次请求使用的 PrincipalSelection 实现
     * @param request 当前请求
     * @param allowedTypes 允许的主体类型
     * @param input Principal 选择输入
     * @return 当前执行主体
     */
    @SuppressWarnings("unchecked")
    public @NotNull Principal select(
            @NotNull PrincipalSelection<?> selection,
            @NotNull CurrentRequest request,
            @NotNull PrincipalType[] allowedTypes,
            @NotNull PrincipalSelectionInput input) {
        PrincipalSelection<Object> typedSelection = (PrincipalSelection<Object>) selection;
        Object parameters = parameterObject(typedSelection.parameterType(), input.getParameters());
        return typedSelection.select(
                request, allowedTypes, input.getPrincipalId(), input.getTenantId(), input.getSelectionKey(), parameters);
    }

    /**
     * 构造选择器声明的参数对象。
     *
     * <p>默认 Map 选择器直接复用请求变量；业务选择器声明强类型参数后，由项目共用 ObjectMapper 完成字段
     * 绑定，避免各选择器重复解释 HTTP 或 STOMP 输入。
     *
     * @param parameterType 选择器声明的参数类型
     * @param variables 请求数据和已绑定方法参数组成的变量映射
     * @return Map 或转换后的强类型选择参数
     */
    @SuppressWarnings("unchecked")
    private @NotNull Object parameterObject(
            @NotNull Class<?> parameterType, @NotNull Map<String, Object> variables) {
        if (Map.class.isAssignableFrom(parameterType)) {
            return variables;
        }
        return objectMapper.convertValue(variables, parameterType);
    }
}
