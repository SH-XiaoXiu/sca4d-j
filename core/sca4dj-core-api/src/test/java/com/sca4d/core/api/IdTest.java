package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.id.Id;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

/** 验证正数标识的不变量、数值转换和比较行为。 */
class IdTest {

    @Test
    void requiresPositiveValueAndSupportsNumberComparisons() {
        Id id = Id.of(10);

        assertEquals(10L, id.longValue());
        assertTrue(id.isGreaterThan(9));
        assertTrue(id.isAtLeast(BigInteger.TEN));
        assertTrue(id.isEqualTo(new BigDecimal("10.0")));
        assertTrue(id.isLessThan(11L));
        assertFalse(id.isEqualTo(11));
        assertEquals(0, id.compareTo(Id.of(10)));
    }

    @Test
    void rejectsNonPositiveAndNonIntegralComparisonValues() {
        assertThrows(IllegalArgumentException.class, () -> Id.of(0));
        assertThrows(IllegalArgumentException.class, () -> Id.of(-1));
        assertThrows(IllegalArgumentException.class, () -> Id.of(1).isGreaterThan(1.5D));
        assertThrows(IllegalArgumentException.class, () -> Id.of(1).isGreaterThan(BigInteger.ZERO));
    }
}
