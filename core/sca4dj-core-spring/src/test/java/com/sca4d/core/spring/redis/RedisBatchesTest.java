package com.sca4d.core.spring.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 验证 Redis 批次按每项命令数限制 pipeline。 */
class RedisBatchesTest {

    @Test
    @DisplayName("按命令数量切批并保持顺序")
    void partitionsByCommandCount() {
        RedisPipelineProperties properties = new RedisPipelineProperties();
        properties.setMaxCommands(5);
        RedisBatches batches = new RedisBatches(properties);
        List<List<Integer>> received = new ArrayList<>();

        List<Integer> result = batches.query(List.of(1, 2, 3, 4, 5), 2, batch -> {
            received.add(batch);
            return batch;
        });

        assertThat(received).containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
        assertThat(result).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("空输入不执行回调并拒绝超过 pipeline 上限的单项")
    void skipsEmptyInputAndRejectsOversizedItem() {
        RedisPipelineProperties properties = new RedisPipelineProperties();
        properties.setMaxCommands(3);
        RedisBatches batches = new RedisBatches(properties);
        AtomicInteger calls = new AtomicInteger();

        batches.forEach(List.of(), 1, batch -> calls.incrementAndGet());

        assertThat(calls).hasValue(0);
        assertThatThrownBy(() -> batches.query(List.of(1), 4, batch -> batch))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
