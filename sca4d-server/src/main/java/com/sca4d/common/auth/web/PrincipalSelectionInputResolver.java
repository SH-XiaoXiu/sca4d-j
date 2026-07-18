package com.sca4d.common.auth.web;

import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.request.CurrentRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.expression.MapAccessor;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP 与 STOMP 场景的 Principal 选择输入解析器。
 */
public class PrincipalSelectionInputResolver {

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 解析 Principal 选择输入。
     *
     * @param annotation Principal 声明
     * @param method 处理方法
     * @param args 已绑定的方法参数
     * @param request 当前请求
     * @return Principal 选择输入
     */
    public @NotNull PrincipalSelectionInput resolve(
            @Nullable AuthPrincipal annotation,
            @NotNull Method method,
            @NotNull Object[] args,
            @NotNull CurrentRequest request) {
        Map<String, Object> parameters = selectionVariables(method, args, request);
        StandardEvaluationContext context = selectionEvaluationContext();
        parameters.forEach(context::setVariable);
        Long principalId = numericSelectionValue(
                evaluateExpression(annotation == null ? "" : annotation.principalId(), context));
        Long tenantId = numericSelectionValue(
                evaluateExpression(annotation == null ? "" : annotation.tenantId(), context));
        String selectionKey = textualSelectionValue(
                evaluateExpression(annotation == null ? "" : annotation.selectionKey(), context));
        return new PrincipalSelectionInput(principalId, tenantId, selectionKey, parameters);
    }

    /**
     * 计算基于当前请求和方法参数的表达式。
     *
     * @param expression SpEL
     * @param method 处理方法
     * @param args 已绑定的方法参数
     * @param request 当前请求
     * @return 表达式结果
     */
    public @Nullable Object evaluate(
            @NotNull String expression,
            @NotNull Method method,
            @NotNull Object[] args,
            @NotNull CurrentRequest request) {
        StandardEvaluationContext context = selectionEvaluationContext();
        selectionVariables(method, args, request).forEach(context::setVariable);
        return evaluateExpression(expression, context);
    }

    /**
     * 判断选择表达式能否在 MVC 参数绑定前求值。
     *
     * @param annotation Principal 声明
     * @param method 处理方法
     * @return 只依赖请求数据时返回 true
     */
    public boolean supportsEarlyResolution(
            @NotNull AuthPrincipal annotation, @NotNull Method method) {
        String[] names = parameterNameDiscoverer.getParameterNames(method);
        return dependsOnlyOnRequestFacts(annotation.principalId(), names)
                && dependsOnlyOnRequestFacts(annotation.tenantId(), names)
                && dependsOnlyOnRequestFacts(annotation.selectionKey(), names);
    }

    /**
     * 判断选择表达式是否可在 Controller 参数绑定前安全求值。
     *
     * @param expression Principal 声明中的 SpEL
     * @param parameterNames 处理方法可发现的参数名称；缺失时不把未知变量视为绑定参数
     * @return 只依赖 path、query、header 等请求数据时返回 true
     */
    private boolean dependsOnlyOnRequestFacts(
            @NotNull String expression, @Nullable String[] parameterNames) {
        if (!StringUtils.hasText(expression)) {
            return true;
        }
        if (expression.contains("#body") || expression.contains("#payload")) {
            return false;
        }
        if (parameterNames == null) {
            return true;
        }
        for (String parameterName : parameterNames) {
            if (expression.contains("#" + parameterName)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 执行 Principal 选择声明中的 SpEL，并把表达式错误转换为拒绝访问。
     *
     * @param expression 要执行的 SpEL；空字符串表示没有显式选择条件
     * @param context 包含请求数据和已绑定参数的求值上下文
     * @return 表达式结果；未声明表达式时为 {@code null}
     * @throws ForbiddenException 表达式无效、访问不存在变量或执行失败时抛出
     */
    private @Nullable Object evaluateExpression(
            @NotNull String expression, @NotNull StandardEvaluationContext context) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        try {
            return expressionParser.parseExpression(expression).getValue(context);
        } catch (RuntimeException exception) {
            throw new ForbiddenException(CommonErrorCode.PRINCIPAL_SELECTION_EXPRESSION_INVALID);
        }
    }

    /**
     * 创建支持请求数据映射点访问的 SpEL 上下文。
     *
     * <p>HTTP 的 path、query、header 与 STOMP header 均以映射保存；注册 {@link MapAccessor} 后，声明可以使用
     * {@code #query.tenantId} 这类固定的点访问语法。键名含连字符时仍需使用下标语法。
     *
     * @return 已配置映射访问器的 SpEL 上下文
     */
    private @NotNull StandardEvaluationContext selectionEvaluationContext() {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.addPropertyAccessor(new RequestFactMapAccessor());
        return context;
    }

    /**
     * 把 Principal 或 Tenant 选择值规范为单个数字标识。
     *
     * <p>重复 query 参数和 STOMP 多值 header 会作为集合进入 SpEL 上下文。它们不能隐式取首项或拼接为
     * 字符串，以免同一请求在不同实现中选择不同主体。
     *
     * @param value SpEL 计算得到的选择值
     * @return 数字标识；未声明选择值时为 {@code null}
     * @throws ForbiddenException 值不是单个可解析数字时抛出
     */
    private @Nullable Long numericSelectionValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        requireSingleSelectionValue(value);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw new ForbiddenException(CommonErrorCode.PRINCIPAL_SELECTION_NUMBER_REQUIRED);
        }
    }

    /**
     * 把业务选择键规范为单个文本值。
     *
     * @param value SpEL 计算得到的选择值
     * @return 文本选择键；未声明选择值时为 {@code null}
     * @throws ForbiddenException 值来自重复参数或多值 header 时抛出
     */
    private @Nullable String textualSelectionValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        requireSingleSelectionValue(value);
        return value.toString();
    }

    /**
     * 创建 Principal 选择 SpEL 可见的变量集合。
     *
     * <p>请求数据始终使用 {@code path}、{@code query}、{@code header}、{@code body}、{@code destination}
     * 和 {@code payload} 变量。可发现的方法参数名称直接映射到已经绑定的参数值。
     *
     * @param method 当前处理方法
     * @param args Spring 已绑定的实参数组
     * @param request 当前请求
     * @return 供表达式执行和 typed PrincipalSelection 转换复用的变量映射
     */
    private @NotNull Map<String, Object> selectionVariables(
            @NotNull Method method, @NotNull Object[] args, @NotNull CurrentRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        String[] names = parameterNameDiscoverer.getParameterNames(method);
        if (names != null) {
            for (int index = 0; index < names.length; index++) {
                parameters.put(names[index], args[index]);
            }
        }
        Object payload = boundPayload(method, args, request.getPayload());
        parameters.put("path", request.getPathVariables());
        parameters.put("query", request.getQueryParameters());
        parameters.put("header", request.getHeaders());
        parameters.put("body", payload);
        parameters.put("destination", request.getPath());
        parameters.put("payload", payload);
        return parameters;
    }

    /**
     * 查找 Controller 或 MessageMapping 已绑定的 body/payload 参数。
     *
     * <p>没有显式 body/payload 参数时使用传输实现放入 {@link CurrentRequest} 的原始 payload，保证 HTTP
     * 与 STOMP 在相同表达式变量下具有一致的回退行为。
     *
     * @param method 当前处理方法
     * @param args Spring 已绑定的实参数组
     * @param fallback 传输实现保存的 payload
     * @return 显式 body/payload 参数或传输 payload；两者都不存在时为 {@code null}
     */
    private @Nullable Object boundPayload(
            @NotNull Method method, @NotNull Object[] args, @Nullable Object fallback) {
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            if (parameters[index].isAnnotationPresent(RequestBody.class)
                    || parameters[index].isAnnotationPresent(Payload.class)) {
                return args[index];
            }
        }
        return fallback;
    }

    /**
     * 拒绝不能唯一表示 Principal 选择输入的集合或数组。
     *
     * @param value SpEL 计算结果
     * @throws ForbiddenException 结果为集合或数组，无法表达唯一选择值时抛出
     */
    private void requireSingleSelectionValue(@NotNull Object value) {
        if (value instanceof Collection<?> || value.getClass().isArray()) {
            throw new ForbiddenException(CommonErrorCode.PRINCIPAL_SELECTION_SCALAR_REQUIRED);
        }
    }
}
