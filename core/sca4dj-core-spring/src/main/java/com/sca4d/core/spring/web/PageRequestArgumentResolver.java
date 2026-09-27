package com.sca4d.core.spring.web;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.config.CoreSpringProperties;
import com.sca4d.core.spring.web.annotation.PageOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Objects;

/** 解析并校验 {@code page} 和 {@code size} 查询参数。 */
public class PageRequestArgumentResolver implements HandlerMethodArgumentResolver {

    private final int defaultSize;

    private final int maxSize;

    /**
     * 创建分页请求参数解析器。
     *
     * @param properties 分页默认值和最大值
     */
    public PageRequestArgumentResolver(CoreSpringProperties properties) {
        CoreSpringProperties checked = Objects.requireNonNull(properties, "Core Spring 配置不能为空");
        defaultSize = checked.getPageDefaultSize();
        maxSize = checked.getPageMaxSize();
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return PageRequest.class.equals(parameter.getParameterType());
    }

    @Override
    public @NotNull PageRequest resolveArgument(
            @NotNull MethodParameter parameter,
            @Nullable ModelAndViewContainer modelAndViewContainer,
            @NotNull NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        return resolvePage(parameter, webRequest);
    }

    /** @return 使用全局配置和参数级上限校验后的分页要求 */
    @NotNull
    PageRequest resolvePage(
            @NotNull MethodParameter parameter,
            @NotNull NativeWebRequest webRequest) {
        try {
            String pageValue = webRequest.getParameter("page");
            String sizeValue = webRequest.getParameter("size");
            int page = pageValue == null || pageValue.isBlank() ? 1 : Integer.parseInt(pageValue);
            int size = sizeValue == null || sizeValue.isBlank()
                    ? defaultSize
                    : Integer.parseInt(sizeValue);
            PageOptions options = parameter.getParameterAnnotation(PageOptions.class);
            int parameterMaxSize = options == null ? maxSize : options.maxSize();
            if (parameterMaxSize < 1) {
                throw new IllegalStateException("PageOptions maxSize 必须为正数");
            }
            if (page < 1 || size < 1 || size > Math.min(maxSize, parameterMaxSize)) {
                throw new BusinessException(CoreErrorCode.VALIDATION_FAILED);
            }
            return new PageRequest(page, size);
        } catch (NumberFormatException exception) {
            throw new BusinessException(CoreErrorCode.VALIDATION_FAILED, exception);
        }
    }

}
