package com.sca4d.core.api.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** 验证 Call 各结果阶段的顺序、短路和异步完成语义。 */
class CallLifecyclePhasesTest {

    @Test
    void completesResultCallbacksAfterAroundUnwinds() throws Exception {
        List<String> events = new ArrayList<>();
        CallLifecycle.Around around = new AroundHook("TEST/AROUND", events);
        CallLifecycle.Before before = new BeforeHook("TEST/BEFORE", events);
        CallLifecycle.AfterReturning returned = new ReturningHook("TEST/RETURN", events);
        CallLifecycle.After after = new AfterHook("TEST/AFTER", events);
        CallLifecycle.Completion completion = new CompletionHook("TEST/COMPLETE", events);
        CallLifecycleConfiguration configuration = new CallLifecycleConfiguration(
                List.of(completion, after, returned, before, around));
        configuration.after(around.code(), before.code());
        CallInvoker invoker = new CallInvoker(configuration);

        assertEquals("done", invoker.execute(execution("invoke"), () -> {
            events.add("target");
            return "done";
        }));
        assertEquals(List.of("before", "around-enter", "target", "around-exit", "return", "after", "complete"), events);
    }

    @Test
    void waitsForAsyncCompletionWithoutBlockingTheEntryThread() throws Exception {
        List<String> events = new ArrayList<>();
        CallLifecycle.Around around = new AroundHook("TEST/AROUND", events);
        CallLifecycle.Completion completion = new CompletionHook("TEST/COMPLETE", events);
        CallInvoker invoker = new CallInvoker(List.of(around, completion));
        CompletableFuture<String> target = new CompletableFuture<>();

        CompletionStage<String> result = invoker.executeAsync(execution("invokeAsync"), () -> target);
        assertFalse(result.toCompletableFuture().isDone());
        target.complete("done");

        assertEquals("done", result.toCompletableFuture().join());
        assertEquals(List.of("around-enter", "around-exit", "complete"), events);
    }

    @Test
    void recordsCompletionEvenWhenCallerCancelsWaiting() throws Exception {
        List<String> events = new ArrayList<>();
        CallExecution execution = execution("invokeAsync");
        CallInvoker invoker = new CallInvoker(List.of(new CompletionHook("TEST/COMPLETE", events)));
        CompletableFuture<String> target = new CompletableFuture<>();

        CompletionStage<String> response = invoker.executeAsync(execution, () -> target);
        response.toCompletableFuture().cancel(true);
        assertFalse(target.isDone());
        target.complete("done");

        assertEquals(CallOutcome.Status.SUCCESS, execution.outcome().orElseThrow().status());
        assertEquals(List.of("complete"), events);
    }

    @Test
    void runsFailureAndCompletionCallbacksWithoutChangingOriginalFailure() throws Exception {
        List<String> events = new ArrayList<>();
        CallInvoker invoker = new CallInvoker(
                List.of(
                        new ReturningHook("TEST/RETURN", events),
                        new ThrowingHook("TEST/THROW", events),
                        new AfterHook("TEST/AFTER", events),
                        new CompletionHook("TEST/COMPLETE", events)));
        IllegalStateException original = new IllegalStateException("target failed");

        assertSame(
                original,
                assertThrows(
                        IllegalStateException.class,
                        () -> invoker.execute(execution("invoke"), () -> {
                            throw original;
                        })));
        assertEquals(List.of("throw", "after", "complete"), events);
    }

    @Test
    void validatesDependenciesAndStageImplementations() {
        CallLifecycle.Before before = new BeforeHook("TEST/BEFORE", new ArrayList<>());
        CallLifecycle.Completion completion = new CompletionHook("TEST/COMPLETE", new ArrayList<>());
        CallLifecycleConfiguration configuration = new CallLifecycleConfiguration(List.of(completion, before));
        configuration.after(completion.code(), CallLifecycle.BEFORE);
        new CallInvoker(configuration);

        assertThrows(
                IllegalArgumentException.class,
                () -> configuration.register(CallLifecycle.AROUND, before, Set.of(), Set.of(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CallInvoker(List.of(before, before)));
        configuration.after(before.code(), completion.code());
        assertThrows(IllegalArgumentException.class, () -> new CallInvoker(configuration));
    }

    private static CallExecution execution(String method) throws Exception {
        Method target = Fixture.class.getDeclaredMethod(method);
        return new CallExecution(new Call("test", Instant.EPOCH), Fixture.class, target, new Object[0], String.class);
    }

    private static class Fixture {
        private String invoke() {
            return "done";
        }

        private CompletionStage<String> invokeAsync() {
            return CompletableFuture.completedFuture("done");
        }
    }

    private static class AroundHook implements CallLifecycle.Around {
        private final TreeCode code;

        private final List<String> events;

        private AroundHook(String code, List<String> events) {
            this.code = TreeCode.of(code);
            this.events = events;
        }

        @Override
        public @NotNull TreeCode code() {
            return code;
        }

        @Override
        public @NotNull CallOutcome around(@NotNull CallExecution execution, @NotNull CallLifecycle.Next next) {
            events.add("around-enter");
            CallOutcome outcome = next.proceed();
            events.add("around-exit");
            return outcome;
        }

        @Override
        public @NotNull CompletionStage<CallOutcome> aroundAsync(
                @NotNull CallExecution execution,
                @NotNull CallLifecycle.AsyncNext next) {
            events.add("around-enter");
            return next.proceedAsync().thenApply(outcome -> {
                events.add("around-exit");
                return outcome;
            });
        }
    }

    private static class BeforeHook implements CallLifecycle.Before {
        private final TreeCode code;

        private final List<String> events;

        private BeforeHook(String code, List<String> events) {
            this.code = TreeCode.of(code);
            this.events = events;
        }

        @Override
        public @NotNull TreeCode code() {
            return code;
        }

        @Override
        public void before(@NotNull CallExecution execution) {
            events.add("before");
        }
    }

    private static class ReturningHook implements CallLifecycle.AfterReturning {
        private final TreeCode code;

        private final List<String> events;

        private ReturningHook(String code, List<String> events) {
            this.code = TreeCode.of(code);
            this.events = events;
        }

        @Override
        public @NotNull TreeCode code() {
            return code;
        }

        @Override
        public void afterReturning(@NotNull CallExecution execution, @NotNull CallOutcome outcome) {
            events.add("return");
        }
    }

    private static class AfterHook implements CallLifecycle.After {
        private final TreeCode code;

        private final List<String> events;

        private AfterHook(String code, List<String> events) {
            this.code = TreeCode.of(code);
            this.events = events;
        }

        @Override
        public @NotNull TreeCode code() {
            return code;
        }

        @Override
        public void after(@NotNull CallExecution execution, @NotNull CallOutcome outcome) {
            events.add("after");
        }
    }

    private static class ThrowingHook implements CallLifecycle.AfterThrowing {
        private final TreeCode code;

        private final List<String> events;

        private ThrowingHook(String code, List<String> events) {
            this.code = TreeCode.of(code);
            this.events = events;
        }

        @Override
        public @NotNull TreeCode code() {
            return code;
        }

        @Override
        public void afterThrowing(@NotNull CallExecution execution, @NotNull CallOutcome outcome) {
            events.add("throw");
        }
    }

    private static class CompletionHook implements CallLifecycle.Completion {
        private final TreeCode code;

        private final List<String> events;

        private CompletionHook(String code, List<String> events) {
            this.code = TreeCode.of(code);
            this.events = events;
        }

        @Override
        public @NotNull TreeCode code() {
            return code;
        }

        @Override
        public void complete(@NotNull CallExecution execution, @NotNull CallOutcome outcome) {
            events.add("complete");
        }
    }
}
