package com.sca4d.core.api.call;

import org.jetbrains.annotations.NotNull;

/**
 * 为从方法参数取值的解析器提供统一的参数检查。
 *
 * <p>例如解析 {@code update(long resourceId, ChangeRequest request)} 时，
 * 子类可用 {@code longArgument(execution, 0)} 读取资源 ID，再执行自己的查询。
 * 参数缺失或类型错误表示注解与方法签名不匹配，抛出配置错误；不会把错误参数当成空资源放行。
 * 本类不查询业务数据，也不保存调用状态，可由单例解析器继承。
 *
 * @param <T> 注解需要的解析结果，例如限流键或资源集合
 */
public abstract class AbstractCallValueResolver<T> implements CallValueResolver<T> {

    /**
     * 读取指定位置的非空参数；索引从零开始。
     *
     * @param execution 当前方法执行
     * @param index 参数位置
     * @param type 参数类型，应使用包装类型而非基本类型
     * @param <A> 参数类型
     * @return 通过类型检查的参数
     * @throws IllegalStateException 参数缺失、为空或类型不符
     */
    protected final <A> @NotNull A argument(CallExecution execution, int index, Class<A> type) {
        Object[] arguments = execution.arguments();
        if (index < 0 || index >= arguments.length || !type.isInstance(arguments[index])) {
            throw new IllegalStateException(
                    getClass().getSimpleName() + " 要求第 " + (index + 1)
                            + " 个实参为非空 " + type.getSimpleName());
        }
        return type.cast(arguments[index]);
    }

    /** 读取数字参数并转换为 long；例如方法的第一个资源 ID 使用索引 0。 */
    protected final long longArgument(CallExecution execution, int index) {
        return argument(execution, index, Number.class).longValue();
    }
}
