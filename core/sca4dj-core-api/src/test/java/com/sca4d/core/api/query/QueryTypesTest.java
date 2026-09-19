package com.sca4d.core.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class QueryTypesTest {

    @Test
    @DisplayName("Calculates Page Offset Without Integer Overflow")
    void calculatesPageOffsetWithoutIntegerOverflow() {
        assertEquals(429496729200L, new PageRequest(Integer.MAX_VALUE, 200).offset());
    }

    @Test
    @DisplayName("Copies And Maps Page Items")
    void copiesAndMapsPageItems() {
        List<String> source = new ArrayList<>(List.of("1", "2"));
        Page<String> page = new Page<>(source, new PageRequest(2, 2), 5);
        source.clear();

        Page<Integer> mapped = page.map(Integer::valueOf);

        assertEquals(List.of("1", "2"), page.getItems());
        assertEquals(List.of(1, 2), mapped.getItems());
        assertEquals(2, mapped.getPage());
        assertEquals(2, mapped.getSize());
        assertEquals(5, mapped.getTotal());
    }

    @Test
    @DisplayName("Rejects Duplicate List Sort Fields")
    void rejectsDuplicateListSortFields() {
        Sort status = Sort.of("STATUS");
        assertThrows(
                IllegalArgumentException.class,
                () -> new ListRequest(
                        new PageRequest(1, 20),
                        List.of(status.asc(), status.desc())));
    }

    @Test
    @DisplayName("Reuses Common Sort Fields And Validates Custom Names")
    void reusesCommonSortFieldsAndValidatesCustomNames() {
        assertSame(Sort.UPDATED_AT, Sort.of("UPDATED_AT"));
        assertEquals("STATUS", Sort.of("STATUS").name());
        assertThrows(IllegalArgumentException.class, () -> Sort.of("status"));
    }
}
