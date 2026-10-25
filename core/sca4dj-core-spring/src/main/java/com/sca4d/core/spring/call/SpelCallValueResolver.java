package com.sca4d.core.spring.call;

import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.spring.exception.CoreSpringException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 使用 SpEL 从调用注解读取参数和执行结果，只缓存表达式，不缓存调用数据。 */
public class SpelCallValueResolver implements SPICallValueResolver {
    private static final Logger LOG = LoggerFactory.getLogger(SpelCallValueResolver.class);

    private final SpelExpressionParser parser = new SpelExpressionParser();

    private final ConcurrentMap<String, Expression> expressions = new ConcurrentHashMap<>();

    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    private final BeanFactoryResolver beans;

    /** 创建支持 Spring Bean 引用的注解表达式解析器。 */
    public SpelCallValueResolver(@NotNull ApplicationContext applicationContext) {
        beans = new BeanFactoryResolver(Objects.requireNonNull(applicationContext, "Spring 容器不能为空"));
    }

    /**
     * 按普通 SpEL 读取方法参数、Bean 引用或已经确定的执行结果。
     *
     * @param expression 非空白 SpEL
     * @param execution 当前方法执行
     * @return 表达式原始值，包括明确的空值
     * @throws IllegalStateException 表达式为空白、解析失败、引用未定义变量，或在调用前读取 `result`、`error`、`outcome` 时抛出
     */
    @Override
    public @Nullable Object resolve(@NotNull String expression, @NotNull CallExecution execution) {
        if (expression.isBlank()) {
            throw new IllegalStateException("注解表达式不能为空白");
        }
        try {
            Expression parsed = expressions.computeIfAbsent(expression, parser::parseExpression);
            CallEvaluationContext context = new CallEvaluationContext(execution, parameterNames);
            context.setBeanResolver(beans);
            Object value = parsed.getValue(context);
            LOG.trace(
                    "注解表达式取值完成 | callId={} method={} valueType={}",
                    execution.call().callId(),
                    execution.method().getName(),
                    value == null ? null : value.getClass().getName());
            return value;
        } catch (ExpressionException exception) {
            throw new CoreSpringException("注解表达式解析或取值失败: " + execution.method().toGenericString(), exception);
        }
    }

    /** 为一次取值绑定参数别名，并阻止调用前读取执行结果。 */
    private static final class CallEvaluationContext extends MethodBasedEvaluationContext {
        private final CallExecution execution;

        private final Set<String> variables = new HashSet<>(Set.of("args", "call", "execution"));

        private CallEvaluationContext(CallExecution execution, ParameterNameDiscoverer parameterNames) {
            super(execution, execution.method(), execution.arguments(), parameterNames);
            this.execution = execution;
            String[] names = parameterNames.getParameterNames(execution.method());
            if (names != null) {
                variables.addAll(Arrays.asList(names));
            }
            Object[] arguments = execution.arguments();
            for (int index = 0; index < arguments.length; index++) {
                variables.add("p" + index);
                variables.add("a" + index);
                setVariable("p" + index, arguments[index]);
                setVariable("a" + index, arguments[index]);
            }
            setVariable("args", arguments);
            setVariable("call", execution.call());
            setVariable("execution", execution);
        }

        @Override
        public @Nullable Object lookupVariable(@NonNull String name) {
            if (Set.of("result", "error", "outcome").contains(name)) {
                CallOutcome outcome = execution.outcome()
                        .orElseThrow(() -> new IllegalStateException("调用前不能读取结果变量: " + name));
                return switch (name) {
                    case "result" -> outcome.result();
                    case "error" -> outcome.error();
                    default -> outcome;
                };
            }
            if (!variables.contains(name)) {
                throw new IllegalStateException("未定义的注解表达式变量: " + name);
            }
            return super.lookupVariable(name);
        }
    }
}
