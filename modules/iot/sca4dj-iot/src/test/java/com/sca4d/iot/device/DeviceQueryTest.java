package com.sca4d.iot.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.query.ListRequest;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.query.Sort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证 DeviceQuery 区分有界标识读取和必须分页的列表查询。 */
class DeviceQueryTest {

    @Test
    @DisplayName("有界标识查询去重并保留首次出现顺序")
    void keepsUniqueDeviceIdsInInputOrder() {
        DeviceQuery query = DeviceQuery.forIds(List.of(42L, 41L, 42L)).build();

        assertThat(query.deviceIds()).containsExactly(42L, 41L);
        assertThat(query.request().getPage().page()).isEqualTo(1);
        assertThat(query.request().getPage().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("分页列表拒绝 Device 未声明的排序字段")
    void rejectsUnsupportedListSort() {
        assertThatThrownBy(
                () -> DeviceQuery.list(
                        new ListRequest(
                                new PageRequest(1, 20),
                                List.of(Sort.of("PROTOCOL").asc()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("排序字段");
    }

    @Test
    @DisplayName("分页列表拒绝超过公共 Device 页大小的请求")
    void rejectsOversizedPage() {
        assertThatThrownBy(
                () -> DeviceQuery.list(
                        new ListRequest(
                                new PageRequest(1, 101),
                                List.of(Sort.CREATED_AT.desc()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("每页数量");
    }
}
