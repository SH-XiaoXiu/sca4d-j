package com.sca4d.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybatisflex.core.paginate.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证分页记录、总数、页码和每页数量的构造结果。 */
class PageDataTest {

    @Test
    void pageDataShouldConvertFromMybatisFlexPage() {
        Page<String> page = new Page<>(2, 10);
        page.setRecords(List.of("a", "b"));
        page.setTotalRow(25);

        PageData<Integer> data = PageData.from(page, String::length);

        assertThat(data.getPage()).isEqualTo(2);
        assertThat(data.getSize()).isEqualTo(10);
        assertThat(data.getTotal()).isEqualTo(25);
        assertThat(data.getRecords()).containsExactly(1, 1);
    }

    @Test
    void pageDataShouldMapRecordsAndKeepPageMetadata() {
        PageData<String> page = new PageData<>(List.of("alice", "bob"), 3, 20, 45);

        PageData<Integer> mapped = page.map(String::length);

        assertThat(mapped.getPage()).isEqualTo(3);
        assertThat(mapped.getSize()).isEqualTo(20);
        assertThat(mapped.getTotal()).isEqualTo(45);
        assertThat(mapped.getRecords()).containsExactly(5, 3);
    }

    @Test
    void pageQueryShouldNormalizeBoundary() {
        PageQuery query = new PageQuery(-1L, 1000L);

        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(500);
    }
}
