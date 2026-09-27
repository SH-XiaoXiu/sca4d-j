package com.sca4d.core.spring.web;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.config.CoreSpringProperties;
import com.sca4d.core.spring.web.list.ListParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 解析列表接口的 {@code page}、{@code size} 和重复 {@code sort} 查询参数。 */
public class ListParametersArgumentResolver implements HandlerMethodArgumentResolver {

    private final @NotNull PageRequestArgumentResolver pages;

    private final int maxSortFields;

    /**
     * 创建列表参数解析器。
     *
     * @param properties 分页默认值、分页上限和排序字段数量上限
     */
    public ListParametersArgumentResolver(@NotNull CoreSpringProperties properties) {
        CoreSpringProperties checked = Objects.requireNonNull(properties, "Core Spring 配置不能为空");
        this.pages = new PageRequestArgumentResolver(checked);
        this.maxSortFields = checked.getSortMaxFields();
    }

    @Override
    public boolean supportsParameter(@NotNull MethodParameter parameter) {
        return ListParameters.class.equals(parameter.getParameterType());
    }

    /**
     * 读取列表公共参数并保留 sort 参数的原始顺序。
     *
     * @throws BusinessException 参数无效或仍使用 sortBy、sortDirection 时抛出
     */
    @Override
    public @NotNull Object resolveArgument(
            @NotNull MethodParameter parameter,
            @Nullable ModelAndViewContainer modelAndViewContainer,
            @NotNull NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        if (webRequest.getParameter("sortBy") != null
                || webRequest.getParameter("sortDirection") != null) {
            throw new BusinessException(CoreErrorCode.VALIDATION_FAILED);
        }
        PageRequest page = pages.resolvePage(parameter, webRequest);
        String[] values = webRequest.getParameterValues("sort");
        List<String> sort = values == null ? List.of() : Arrays.asList(values);
        if (sort.size() > maxSortFields) {
            throw new BusinessException(CoreErrorCode.VALIDATION_FAILED);
        }
        return new ListParameters(page, sort, maxSortFields);
    }

}
