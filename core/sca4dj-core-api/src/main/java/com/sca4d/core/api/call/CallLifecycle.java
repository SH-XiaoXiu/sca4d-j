package com.sca4d.core.api.call;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** 定义 Call 参与者可以介入的通用生命周期语义。 */
public enum CallLifecycle {
    /** 在实际方法前校验或准备。 */
    BEFORE,
    /** 使用 Next 包围剩余调用，可以短路或观察最终结果。 */
    AROUND,
    /** 只处理正常返回。 */
    AFTER_RETURNING,
    /** 只处理异常结果。 */
    AFTER_THROWING,
    /** 无论成功失败都执行。 */
    AFTER,
    /** 在事务提交前检查条件。 */
    PRE_COMMIT,
    /** 在事务成功提交后执行。 */
    AFTER_COMMIT,
    /** 在 Call 最终结束时执行清理。 */
    COMPLETION;

    /** 标记调用入口或功能注解；功能注解必须由所属功能注册。 */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    public @interface Intercepted {
    }

    /** 表示业务成功前必须在预提交阶段满足的一项条件。 */
    public interface PreCommit {
        /** 校验条件已经被当前调用满足。 */
        void verifySatisfied();

        /** 只读事务提交时是否也必须校验该条件。 */
        default boolean appliesToReadOnlyTransaction() {
            return true;
        }
    }

    /** 所有生命周期参与者共享的编码和依赖声明。 */
    public interface Participant {
        /** 返回当前功能在生命周期配置中的稳定编码。 */
        @NotNull
        TreeCode code();

        /** 返回必须位于当前处理之外的功能编码。 */
        default @NotNull Set<TreeCode> after() {
            return Set.of();
        }

        /** 返回必须位于当前处理之内的功能编码。 */
        default @NotNull Set<TreeCode> before() {
            return Set.of();
        }

        /** 无硬依赖时用于稳定排序的优先级。 */
        default int priority() {
            return 0;
        }

        /** 当前方法是否适用。 */
        default boolean supports(@NotNull CallExecution execution) {
            return true;
        }
    }

    /** 使用 AOP 环绕语义介入调用。实现类的类型即表示其采用 Around 语义。 */
    public interface Around extends Participant {
        /** 处理本层并返回实际路径。 */
        @NotNull
        CallOutcome around(@NotNull CallExecution execution, @NotNull Next next) throws Throwable;

        /** 异步处理本层；没有明确异步实现时失败，避免隐式阻塞。 */
        default @NotNull CompletionStage<CallOutcome> aroundAsync(
                @NotNull CallExecution execution,
                @NotNull AsyncNext next) throws Throwable {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "环绕处理没有定义异步语义: " + code()));
        }
    }

    /** 只在目标调用前执行校验或准备。 */
    public interface Before extends Participant {
        /** 执行调用前逻辑；抛出异常时后续调用不会开始。 */
        void before(@NotNull CallExecution execution) throws Throwable;
    }

    /** 只在调用成功返回后执行观察逻辑。 */
    public interface AfterReturning extends Participant {
        /** 观察已经成功的调用结果。 */
        void afterReturning(@NotNull CallExecution execution, @NotNull CallOutcome outcome) throws Throwable;
    }

    /** 只在调用拒绝或失败后执行观察逻辑。 */
    public interface AfterThrowing extends Participant {
        /** 观察调用失败，不得把失败伪装成成功。 */
        void afterThrowing(@NotNull CallExecution execution, @NotNull CallOutcome outcome) throws Throwable;
    }

    /** 无论调用成功失败都执行的 finally 语义回调。 */
    public interface After extends Participant {
        /** 在目标结果确定后执行收尾逻辑。 */
        void after(@NotNull CallExecution execution, @NotNull CallOutcome outcome) throws Throwable;
    }

    /** Call 最终结束后的清理回调。 */
    public interface Completion extends Participant {
        /** 清理当前 Call 产生的本地状态和租约。 */
        void complete(@NotNull CallExecution execution, @NotNull CallOutcome outcome) throws Throwable;
    }

    /** 事务成功提交后的回调；提交发生在目标方法返回前，不提供最终 CallOutcome。 */
    public interface AfterCommit extends Participant {
        /** 处理当前事务已经提交的事实。 */
        void afterCommit(@NotNull CallExecution execution) throws Throwable;
    }

    /** 在当前线程继续内层处理，返回成功、重放、拒绝或失败。 */
    @FunctionalInterface
    public interface Next {
        /** 执行剩余调用。 */
        @NotNull
        CallOutcome proceed();
    }

    /** 在异步生命周期中继续内层处理。 */
    @FunctionalInterface
    public interface AsyncNext {
        /** 执行剩余调用并返回最终完成阶段。 */
        @NotNull
        CompletionStage<CallOutcome> proceedAsync();
    }
}
