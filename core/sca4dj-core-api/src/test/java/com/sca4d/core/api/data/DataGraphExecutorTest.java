package com.sca4d.core.api.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class DataGraphExecutorTest {

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
