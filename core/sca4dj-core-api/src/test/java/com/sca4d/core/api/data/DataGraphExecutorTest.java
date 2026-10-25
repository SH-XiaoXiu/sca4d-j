package com.sca4d.core.api.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.data.exception.DataAssertionException;
import com.sca4d.core.api.data.exception.DataOperationExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class DataGraphExecutorTest {

    @Test
    @DisplayName("独立分支并行执行，汇合读取两个结果且保持拓扑顺序")
    void executesIndependentBranchesAndMerges() throws Exception {
        DataKey<String> source = DataKey.of("parallelSource", String.class);
        DataKey<Integer> left = DataKey.of("left", Integer.class);
        DataKey<Integer> right = DataKey.of("right", Integer.class);
        DataKey<Integer> sum = DataKey.of("sum", Integer.class);
        CountDownLatch entered = new CountDownLatch(2);
        java.util.function.Function<DataValues, Integer> branch = values -> {
            entered.countDown();
            try {
                if (!entered.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("两个分支必须能同时进入");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
            return values.get(source).length();
        };
        DataGraph graph = DataGraph.builder()
                .input(source)
                .operation(
                        DataOperation
                                .of("sum", List.of(left, right), sum, values -> values.get(left) + values.get(right)))
                .operation(DataOperation.of("left", List.of(source), left, branch))
                .operation(DataOperation.of("right", List.of(source), right, branch))
                .build();
        try (var executor = Executors.newFixedThreadPool(2)) {
            DataValues output = DataGraphExecutor
                    .executeAsync(graph, DataValues.builder().put(source, "abcd").build(), executor)
                    .get(10, TimeUnit.SECONDS);
            assertEquals(8, output.get(sum));
            assertEquals(List.of(source, left, right, sum), List.copyOf(output.asMap().keySet()));
        }
    }

    @Test
    @DisplayName("分支失败不运行其依赖，取消图不会启动尚未执行的节点")
    void failsWithoutPartialResultAndCancelsQueuedNodes() {
        AtomicInteger dependents = new AtomicInteger();
        DataGraph graph = DataGraph.builder()
                .operation(DataOperation.of("fail", List.of(), LENGTH, values -> {
                    throw new IllegalStateException("failure");
                }))
                .operation(DataOperation.of("dependent", List.of(LENGTH), LABEL, values -> {
                    dependents.incrementAndGet();
                    return "bad";
                }))
                .build();
        var failed = DataGraphExecutor.executeAsync(graph, DataValues.builder().build(), Runnable::run);
        assertThrows(CompletionException.class, failed::join);
        assertEquals(0, dependents.get());
        List<Runnable> queued = new java.util.ArrayList<>();
        var cancelled = DataGraphExecutor.executeAsync(graph, DataValues.builder().build(), queued::add);
        assertTrue(cancelled.cancel(true));
        List.copyOf(queued).forEach(Runnable::run);
        assertEquals(0, dependents.get());
    }

    private static final DataKey<String> SOURCE = DataKey.of("source", String.class);

    private static final DataKey<Integer> LENGTH = DataKey.of("length", Integer.class);

    private static final DataKey<String> LABEL = DataKey.of("label", String.class);

    @Test
    @DisplayName("Executes In Stable Topological Order")
    void executesInStableTopologicalOrder() {
        DataOperation<String> label = DataOperation.of(
                "label",
                List.of(LENGTH),
                LABEL,
                values -> "length=" + values.get(LENGTH));
        DataOperation<Integer> length = DataOperation.of(
                "length",
                List.of(SOURCE),
                LENGTH,
                values -> values.get(SOURCE).length());
        DataGraph graph = DataGraph.builder()
                .input(SOURCE)
                .operation(label)
                .operation(length)
                .build();

        DataValues result = DataGraphExecutor.execute(
                graph,
                DataValues.builder().put(SOURCE, "abcd").build());

        assertEquals(List.of(length, label), graph.operations());
        assertEquals("length=4", result.get(LABEL));
    }

    @Test
    @DisplayName("Rejects Missing Dependency And Cycle While Building")
    void rejectsMissingDependencyAndCycleWhileBuilding() {
        DataKey<String> missing = DataKey.of("missing", String.class);
        DataOperation<Integer> missingOperation = DataOperation.of("length", List.of(missing), LENGTH, values -> 1);

        assertTrue(
                assertThrows(
                        IllegalArgumentException.class,
                        () -> DataGraph.builder()
                                .input(SOURCE)
                                .operation(missingOperation)
                                .build())
                        .getMessage()
                        .contains("未声明输入"));

        DataOperation<Integer> length = DataOperation.of("length", List.of(LABEL), LENGTH, values -> 1);
        DataOperation<String> label = DataOperation.of("label", List.of(LENGTH), LABEL, values -> "value");
        assertTrue(
                assertThrows(
                        IllegalArgumentException.class,
                        () -> DataGraph.builder()
                                .operation(length)
                                .operation(label)
                                .build())
                        .getMessage()
                        .contains("存在环"));
    }

    @Test
    @DisplayName("Operation Can Only Read Declared Inputs")
    void operationCanOnlyReadDeclaredInputs() {
        DataOperation<Integer> operation = DataOperation.of(
                "length",
                List.of(),
                LENGTH,
                values -> values.get(SOURCE).length());
        DataGraph graph = DataGraph.builder().input(SOURCE).operation(operation).build();

        DataAssertionException exception = assertThrows(
                DataAssertionException.class,
                () -> DataGraphExecutor.execute(
                        graph,
                        DataValues.builder().put(SOURCE, "value").build()));
        assertEquals("length", exception.operationName());
        assertEquals("length", exception.outputKey());
    }

    @Test
    @DisplayName("Requires Exact Graph Inputs")
    void requiresExactGraphInputs() {
        DataGraph graph = DataGraph.builder().input(SOURCE).build();

        DataValues values = DataValues.builder().put(SOURCE, "value").build();
        assertEquals("value", values.find(SOURCE).orElseThrow());
        assertTrue(values.find(LENGTH).isEmpty());

        DataAssertionException missing = assertThrows(
                DataAssertionException.class,
                () -> DataGraphExecutor.execute(graph, DataValues.builder().build()));
        assertTrue(missing.getMessage().contains("missing=[source]"));

        DataValues unexpected = DataValues.builder()
                .put(SOURCE, "value")
                .put(LENGTH, 5)
                .build();
        DataAssertionException extra = assertThrows(
                DataAssertionException.class,
                () -> DataGraphExecutor.execute(graph, unexpected));
        assertTrue(extra.getMessage().contains("unexpected=[length]"));
    }

    @Test
    @DisplayName("Classifies Null Type And Unexpected Failures")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void classifiesNullTypeAndUnexpectedFailures() {
        DataOperation<String> nullOutput = DataOperation.of("null-output", List.of(), LABEL, values -> null);
        DataAssertionException nullFailure = assertThrows(
                DataAssertionException.class,
                () -> DataGraphExecutor.execute(
                        DataGraph.builder().operation(nullOutput).build(),
                        DataValues.builder().build()));
        assertEquals("null-output", nullFailure.operationName());

        DataOperation wrongType = DataOperation.of(
                "wrong-type",
                List.of(),
                (DataKey) LABEL,
                values -> 42);
        DataAssertionException typeFailure = assertThrows(
                DataAssertionException.class,
                () -> DataGraphExecutor.execute(
                        DataGraph.builder().operation(wrongType).build(),
                        DataValues.builder().build()));
        assertEquals("wrong-type", typeFailure.operationName());

        IllegalStateException cause = new IllegalStateException("broken");
        DataOperation<String> unexpected = DataOperation.of(
                "unexpected",
                List.of(),
                LABEL,
                values -> {
                    throw cause;
                });
        DataOperationExecutionException executionFailure = assertThrows(
                DataOperationExecutionException.class,
                () -> DataGraphExecutor.execute(
                        DataGraph.builder().operation(unexpected).build(),
                        DataValues.builder().build()));
        assertInstanceOf(IllegalStateException.class, executionFailure.getCause());
        assertEquals(cause, executionFailure.getCause());
        assertEquals("unexpected", executionFailure.operationName());
        assertEquals("label", executionFailure.outputKey());
    }

    @Test
    @DisplayName("Preserves Field And Explicit Safe Sample At Node Boundary")
    void preservesFieldAndExplicitSafeSampleAtNodeBoundary() {
        DataOperation<String> operation = DataOperation.of(
                "validate",
                List.of(),
                LABEL,
                values -> {
                    throw new DataAssertionException("payload.name", "invalid", "redacted");
                });

        DataAssertionException exception = assertThrows(
                DataAssertionException.class,
                () -> DataGraphExecutor.execute(
                        DataGraph.builder().operation(operation).build(),
                        DataValues.builder().build()));

        assertEquals("payload.name", exception.field());
        assertEquals("validate", exception.operationName());
        assertEquals("label", exception.outputKey());
        assertEquals("redacted", exception.safeSample());
        assertInstanceOf(DataAssertionException.class, exception.getCause());
        assertNull(exception.getCause().getCause());
    }
}
