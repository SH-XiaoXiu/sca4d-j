package com.sca4d.common.auth.web;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;

import java.util.Map;

/**
 * 支持以点语法读取可选请求数据的 SpEL Map 访问器。
 *
 * <p>路径、查询和请求头中的缺失键属于正常的可选输入，读取时返回 {@code null}；是否必须提供该值由具体
 * PrincipalSelection 或 ResourceAccessPolicy 契约判断。
 */
public class RequestFactMapAccessor extends MapAccessor {

    /**
     * 判断当前目标是否为请求数据映射。
     *
     * @param context 当前 SpEL 求值上下文
     * @param target 待访问目标
     * @param name 待读取属性名
     * @return 目标为 Map 时返回 {@code true}
     */
    @Override
    public boolean canRead(
            @NotNull EvaluationContext context, @Nullable Object target, @NotNull String name) {
        return target instanceof Map<?, ?>;
    }

    /**
     * 读取一个可选请求数据。
     *
     * @param context 当前 SpEL 求值上下文
     * @param target 已由 {@link #canRead(EvaluationContext, Object, String)} 确认的请求数据映射
     * @param name 待读取属性名
     * @return 包含实际值的 SpEL 类型值，键不存在时值为 {@code null}
     * @throws AccessException 框架未按访问器协定传入 Map 时抛出
     */
    @Override
    public @NotNull TypedValue read(
            @NotNull EvaluationContext context, @Nullable Object target, @NotNull String name)
            throws AccessException {
        if (!(target instanceof Map<?, ?> facts)) {
            throw new AccessException("请求事实目标必须是 Map");
        }
        return new TypedValue(facts.get(name));
    }
}
