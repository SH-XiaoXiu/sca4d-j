package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.concurrent.exception.OptimisticLockException;
import com.sca4d.core.api.error.CoreErrorCode;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.flow.FlowCatalog;
import com.sca4d.core.api.flow.FlowDefinition;
import com.sca4d.core.api.flow.FlowEngine;
import com.sca4d.core.api.flow.FlowEvent;
import com.sca4d.core.api.flow.FlowGraph;
import com.sca4d.core.api.flow.FlowId;
import com.sca4d.core.api.flow.FlowInstance;
import com.sca4d.core.api.flow.FlowNode;
import com.sca4d.core.api.flow.FlowNodeType;
import com.sca4d.core.api.flow.FlowStore;
import com.sca4d.core.api.flow.FlowTransition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class FlowEngineTest {

    private static final Instant START = Instant.parse("2026-07-20T00:00:00Z");

    @Test
    @DisplayName("Advances Generic Steps And Consumes Result Once")
    void advancesGenericStepsAndConsumesResultOnce() {
        MemoryFlowStore store = new MemoryFlowStore();
        FlowDefinition definition = testDefinition(Duration.ofMinutes(5));
        FlowEngine engine = new FlowEngine(new FlowCatalog(List.of(definition)), store);
        FlowId flowId = new FlowId("review-1");

        FlowInstance started = engine.start(flowId, definition.name(), definition.version(), Map.of(), START);
        FlowInstance review = engine.advance(
                flowId,
                new FlowEvent("submitted", Map.of()),
                START.plusSeconds(10));
        FlowInstance completed = engine.advance(
                flowId,
                new FlowEvent("approved", Map.of()),
                START.plusSeconds(20));
        FlowInstance consumed = engine.consume(flowId, START.plusSeconds(30));

        assertEquals("draft", started.currentNodeId());
        assertEquals("review", review.currentNodeId());
        assertEquals("completed", completed.currentNodeId());
        assertTrue(consumed.isConsumed());
        BusinessException consumedAgain = assertThrows(
                BusinessException.class,
                () -> engine.consume(flowId, START.plusSeconds(31)));
        assertEquals(CoreErrorCode.FLOW_RESULT_CONSUMED, consumedAgain.errorCode());
    }

    @Test
    @DisplayName("Rejects Expired Advance")
    void rejectsExpiredAdvance() {
        MemoryFlowStore store = new MemoryFlowStore();
        FlowDefinition definition = testDefinition(Duration.ofSeconds(30));
        FlowEngine engine = new FlowEngine(new FlowCatalog(List.of(definition)), store);

        FlowId expiredId = new FlowId("review-expired");
        FlowInstance expiredStarted = engine.start(expiredId, definition.name(), 1, Map.of(), START);
        assertEquals("draft", expiredStarted.currentNodeId());
        BusinessException expired = assertThrows(
                BusinessException.class,
                () -> engine.advance(
                        expiredId,
                        new FlowEvent("submitted", Map.of()),
                        START.plusSeconds(30)));
        assertEquals(CoreErrorCode.FLOW_EXPIRED, expired.errorCode());
    }

    @Test
    @DisplayName("Rejects Unsaved State After Optimistic Lock Conflict")
    void rejectsUnsavedStateAfterOptimisticLockConflict() {
        MemoryFlowStore store = new MemoryFlowStore();
        store.rejectUpdates = true;
        FlowDefinition definition = testDefinition(Duration.ofMinutes(1));
        FlowEngine engine = new FlowEngine(new FlowCatalog(List.of(definition)), store);
        FlowId flowId = new FlowId("conflict");
        FlowInstance started = engine.start(flowId, definition.name(), 1, Map.of(), START);
        assertEquals("draft", started.currentNodeId());

        assertThrows(
                OptimisticLockException.class,
                () -> engine.advance(
                        flowId,
                        new FlowEvent("submitted", Map.of()),
                        START.plusSeconds(1)));
        assertEquals("draft", store.find(flowId).orElseThrow().currentNodeId());
    }

    private static FlowDefinition testDefinition(Duration timeToLive) {
        return new FlowDefinition() {
            private final FlowGraph graph = new FlowGraph(
                    "draft",
                    List.of(
                            new FlowNode("draft", FlowNodeType.STEP),
                            new FlowNode("review", FlowNodeType.STEP),
                            new FlowNode("completed", FlowNodeType.TERMINAL)),
                    List.of(
                            new FlowTransition("draft", "review", "submitted"),
                            new FlowTransition("review", "completed", "approved")));

            @Override
            public String name() {
                return "test.review";
            }

            @Override
            public int version() {
                return 1;
            }

            @Override
            public FlowGraph graph() {
                return graph;
            }

            @Override
            public Duration timeToLive() {
                return timeToLive;
            }
        };
    }

    private static class MemoryFlowStore implements FlowStore {

        private final Map<FlowId, FlowInstance> instances = new HashMap<>();

        private boolean rejectUpdates;

        @Override
        public boolean create(FlowInstance instance) {
            return instances.putIfAbsent(instance.id(), instance) == null;
        }

        @Override
        public Optional<FlowInstance> find(FlowId flowId) {
            return Optional.ofNullable(instances.get(flowId));
        }

        @Override
        public boolean update(Version expectedVersion, FlowInstance instance) {
            if (rejectUpdates) {
                return false;
            }
            FlowInstance current = instances.get(instance.id());
            if (current == null || current.version().value() != expectedVersion.value()) {
                return false;
            }
            instances.put(instance.id(), instance);
            return true;
        }
    }
}
