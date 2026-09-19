package com.sca4d.core.spring.web.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.query.ListRequest;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.query.Sort;
import com.sca4d.core.api.query.SortDir;
import com.sca4d.core.api.query.SortOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class ListDefinitionTest {

    private static final Sort STATUS = Sort.of("STATUS");

    private static final ListDefinition DEFINITION = ListDefinition.builder()
            .fields(Sort.UPDATED_AT, STATUS)
            .defaultSort(Sort.UPDATED_AT.desc())
            .maxSize(100)
            .build();

    @Test
    @DisplayName("Uses Typed Default Sort When Sort Is Missing")
    void usesTypedDefaultSortWhenSortIsMissing() {
        ListRequest request = parameters(List.of()).resolve(DEFINITION);

        assertThat(request.getSort()).singleElement().satisfies(order -> {
            assertThat(order.getField()).isEqualTo(Sort.UPDATED_AT);
            assertThat(order.getDirection()).isEqualTo(SortDir.DESC);
        });
    }

    @Test
    @DisplayName("Preserves Repeated Sort Parameter Order")
    void preservesRepeatedSortParameterOrder() {
        ListRequest request = parameters(List.of("STATUS,ASC", "UPDATED_AT,DESC"))
                .resolve(DEFINITION);

        assertThat(request.getSort())
                .extracting(SortOrder::getField)
                .containsExactly(STATUS, Sort.UPDATED_AT);
    }

    @Test
    @DisplayName("Rejects Malformed Unknown Duplicate And Excessive Sorts")
    void rejectsInvalidSorts() {
        assertThatThrownBy(() -> parameters(List.of("STATUS")).resolve(DEFINITION))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> parameters(List.of("UNKNOWN,ASC")).resolve(DEFINITION))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> parameters(List.of("STATUS,ASC", "STATUS,DESC")).resolve(DEFINITION))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                () -> new ListParameters(
                        new PageRequest(1, 20),
                        List.of("STATUS,ASC", "UPDATED_AT,DESC"),
                        1).resolve(DEFINITION))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Rejects Endpoint Page Size Overflow")
    void rejectsEndpointPageSizeOverflow() {
        assertThatThrownBy(
                () -> new ListParameters(
                        new PageRequest(1, 101),
                        List.of(),
                        5).resolve(DEFINITION))
                .isInstanceOf(BusinessException.class);
    }

    /** @return 使用当前测试分页和排序限制的原始列表参数 */
    private static ListParameters parameters(List<String> sort) {
        return new ListParameters(new PageRequest(1, 20), sort, 5);
    }
}
