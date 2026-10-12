package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.job.JobNodeOutput;
import com.sca4d.core.api.job.ParallelResults;
import com.sca4d.core.api.job.PipelineJob;
import com.sca4d.core.api.job.PipelineJobPlan;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.pipeline.PipelineExecutionException;
import com.sca4d.core.api.pipeline.PipelineStage;
import com.sca4d.core.api.retry.Backoff;
import com.sca4d.core.api.retry.RetryRule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

class PipelineJobTest {

    private static final RetryRule ONCE = RetryRule.once();

    @Test
    @DisplayName("Pipeline Runs Directly In Declared Order")
    void pipelineRunsDirectlyInDeclaredOrder() {
        List<String> order = new ArrayList<>();
        Pipeline<Integer, String> pipeline = Pipeline
                .of(PipelineStage.of("increment", Integer.class, Integer.class, value -> {
                    order.add("increment");
                    return value + 1;
                }))
                .then(PipelineStage.of("text", Integer.class, String.class, value -> {
                    order.add("text");
                    return "value-" + value;
                }));

        assertEquals("value-3", pipeline.run(2));
        assertEquals(List.of("increment", "text"), order);
        assertEquals(Integer.class, pipeline.inputType());
        assertEquals(String.class, pipeline.outputType());
    }

    @Test
    @DisplayName("Pipeline Rejects Runtime Type Mismatch And Locates Failure")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pipelineRejectsRuntimeTypeMismatchAndLocatesFailure() {
        Pipeline<Integer, Integer> first = Pipeline.of(
                stage("first", Integer.class, Integer.class, value -> value + 1));
        PipelineStage raw = stage("wrong", String.class, String.class, value -> value);

        assertThrows(IllegalArgumentException.class, () -> first.then(raw));

        Pipeline<Integer, Integer> failing = Pipeline.of(
                stage(
                        "failure",
                        Integer.class,
                        Integer.class,
                        value -> {
                            throw new IllegalStateException("broken");
                        }));
        PipelineExecutionException exception = assertThrows(
                PipelineExecutionException.class,
                () -> failing.run(1));
        assertEquals("failure", exception.stageCode());
        assertEquals(IllegalStateException.class, exception.getCause().getClass());

        assertThrows(IllegalArgumentException.class, () -> ((Pipeline) first).run("not-an-integer"));

        PipelineStage wrongOutput = new PipelineStage() {
            @Override
            public @NotNull String code() {
                return "wrong-output";
            }

            @Override
            public @NotNull Class inputType() {
                return Integer.class;
            }

            @Override
            public @NotNull Class outputType() {
                return Integer.class;
            }

            @Override
            public @NotNull Object execute(@NotNull Object input) {
                return "wrong";
            }
        };
        PipelineExecutionException wrongOutputFailure = assertThrows(
                PipelineExecutionException.class,
                () -> Pipeline.of(wrongOutput).run(1));
        assertEquals("wrong-output", wrongOutputFailure.stageCode());
        assertEquals(IllegalStateException.class, wrongOutputFailure.getCause().getClass());
    }

    @Test
    @DisplayName("PipelineJob Plans Parallel Pipelines And Merge As One Node")
    void pipelineJobPlansParallelPipelinesAndMergeAsOneNode() {
        Pipeline<Integer, Integer> seed = Pipeline.of(
                stage("seed", Integer.class, Integer.class, value -> value + 1));
        Pipeline<Integer, String> left = Pipeline.of(
                stage("left", Integer.class, String.class, value -> "L" + value));
        Pipeline<Integer, Long> right = Pipeline.of(
                stage("right", Integer.class, Long.class, Integer::longValue));
        Pipeline<ParallelResults<String, Long>, String> merge = Pipeline.of(
                stage(
                        "merge",
                        ParallelResults.type(),
                        String.class,
                        values -> values.left() + ":" + values.right()));

        TestPipelineJob job = new TestPipelineJob(
                PipelineJobPlan.from(Integer.class)
                        .then("seed", seed, ONCE)
                        .parallel("left", left, ONCE, "right", right, ONCE)
                        .merge("merge", merge, RetryRule.atMost(2, Backoff.fixed(Duration.ofSeconds(1)))));

        PipelineJobPlan<Integer, String> definition = job.definition();
        assertEquals(Integer.class, job.inputType());
        assertEquals(String.class, job.outputType());
        assertEquals(
                List.of("seed", "left", "right", "merge"),
                definition.nodes().stream().map(PipelineJobPlan.Node::key).toList());
        assertEquals(List.of("left", "right"), definition.nodes().getLast().dependencies());
        assertTrue(definition.nodes().getLast().parallelMerge());
    }

    @Test
    @DisplayName("PipelineJob Rejects Duplicate Node Keys")
    void pipelineJobRejectsDuplicateNodeKeys() {
        Pipeline<Integer, Integer> identity = Pipeline.of(
                stage("identity", Integer.class, Integer.class, value -> value));
        PipelineJobPlan<Integer, Integer> plan = PipelineJobPlan.from(Integer.class)
                .then("same", identity, ONCE);

        assertThrows(IllegalArgumentException.class, () -> plan.then("same", identity, ONCE));
    }

    @Test
    @DisplayName("PipelineJob Creates Only Type-Compatible Node Output References")
    void pipelineJobCreatesOnlyTypeCompatibleNodeOutputReferences() {
        Pipeline<Integer, String> text = Pipeline.of(
                stage("text", Integer.class, String.class, Object::toString));
        TestPipelineJob job = new TestPipelineJob(
                PipelineJobPlan.from(Integer.class).then("outcome", text, ONCE));

        JobNodeOutput<String> output = job.nodeOutput("outcome", String.class);

        assertEquals(job.key(), output.jobKey());
        assertEquals(job.definitionVersion(), output.definitionVersion());
        assertEquals("outcome", output.nodeKey());
        assertEquals(String.class, output.outputType());
        assertThrows(IllegalArgumentException.class, () -> assertNotNull(job.nodeOutput("missing", String.class)));
        assertThrows(IllegalArgumentException.class, () -> assertNotNull(job.nodeOutput("outcome", Long.class)));
    }

    /** 创建声明具体输入输出 Class 的测试阶段。 */
    private static <I, O> PipelineStage<I, O> stage(
            String code,
            Class<I> inputType,
            Class<O> outputType,
            Function<I, O> function) {
        return new PipelineStage<>() {
            @Override
            public @NotNull String code() {
                return code;
            }

            @Override
            public @NotNull Class<I> inputType() {
                return inputType;
            }

            @Override
            public @NotNull Class<O> outputType() {
                return outputType;
            }

            @Override
            public @NotNull O execute(@NotNull I input) {
                return function.apply(input);
            }
        };
    }

    /** 使用测试传入计划的 PipelineJob。 */
    private static class TestPipelineJob extends PipelineJob<Integer, String> {

        private final PipelineJobPlan<Integer, String> plan;

        private TestPipelineJob(PipelineJobPlan<Integer, String> plan) {
            this.plan = plan;
        }

        @Override
        public @NotNull JobKey key() {
            return new JobKey("TEST/PIPELINE");
        }

        @Override
        public int definitionVersion() {
            return 1;
        }

        @Override
        protected @NotNull PipelineJobPlan<Integer, String> plan() {
            return plan;
        }
    }
}
