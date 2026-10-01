package com.sca4d.core.spring.persistence.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 验证 SQL 批次同时遵守条目和绑定参数限制。 */
class SqlBatchesTest {

    @Test
    @DisplayName("按较小限制切批并保持输入与结果顺序")
    void partitionsBySmallerLimitAndPreservesOrder() {
        SqlBatchProperties properties = new SqlBatchProperties();
        properties.setMaxItems(4);
        properties.setMaxBindParameters(10);
        SqlBatches batches = new SqlBatches(properties);
        List<List<Integer>> received = new ArrayList<>();

        List<String> result = batches.query(
                List.of(1, 2, 2, 3, 4, 5),
                3,
                1,
                batch -> {
                    received.add(batch);
                    return batch.stream().map(String::valueOf).toList();
                });

        assertThat(received).containsExactly(List.of(1, 2, 2), List.of(3, 4, 5));
        assertThat(result).containsExactly("1", "2", "2", "3", "4", "5");
    }

    @Test
    @DisplayName("空输入不执行回调且写操作使用相同批次计算")
    void skipsEmptyInputAndUsesSamePartitionsForWrites() {
        SqlBatchProperties properties = new SqlBatchProperties();
        properties.setMaxItems(2);
        SqlBatches batches = new SqlBatches(properties);
        AtomicInteger calls = new AtomicInteger();
        List<List<Integer>> received = new ArrayList<>();

        assertThat(batches.query(List.of(), 1, 0, batch -> {
            calls.incrementAndGet();
            return batch;
        }))
                .isEmpty();
        batches.forEach(List.of(1, 2, 3), 1, 0, received::add);

        assertThat(calls).hasValue(0);
        assertThat(received).containsExactly(List.of(1, 2), List.of(3));
    }

    @Test
    @DisplayName("拒绝空元素、空结果和无法容纳单项的参数形状")
    void rejectsInvalidInputsAndResults() {
        SqlBatchProperties properties = new SqlBatchProperties();
        properties.setMaxBindParameters(5);
        SqlBatches batches = new SqlBatches(properties);

        assertThatThrownBy(() -> batches.query(Arrays.asList(1, null), 1, 0, batch -> batch))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> batches.query(List.of(1), 1, 0, batch -> null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> batches.query(List.of(1), 3, 3, batch -> batch))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
