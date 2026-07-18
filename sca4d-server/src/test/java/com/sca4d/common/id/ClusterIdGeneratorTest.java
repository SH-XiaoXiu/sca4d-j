package com.sca4d.common.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 验证集群 ID 生成器返回正数且连续调用不重复。 */
class ClusterIdGeneratorTest {

    @Test
    void shouldGenerateClusterId() {
        ClusterIdGenerator generator = new ClusterIdGenerator(1, 1);

        assertThat(generator.nextId()).isPositive();
        assertThat(generator.nextIdStr()).isNotBlank();
    }

    @Test
    void shouldRejectInvalidWorkerId() {
        assertThatThrownBy(() -> new ClusterIdGenerator(ClusterIdGenerator.MAX_WORKER_ID + 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workerId");
    }
}
