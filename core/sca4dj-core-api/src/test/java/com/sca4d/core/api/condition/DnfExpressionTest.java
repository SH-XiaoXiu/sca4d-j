package com.sca4d.core.api.condition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class DnfExpressionTest {

    @Test
    @DisplayName("Empty Clause Is True And Empty Expression Is False")
    void emptyClauseIsTrueAndEmptyExpressionIsFalse() {
        assertTrue(new DnfClause<String>(List.of()).test("value"));
        assertFalse(new DnfExpression<String>(List.of()).test("value"));
    }

    @Test
    @DisplayName("Clauses Are Or And Conditions Inside Clause Are And")
    void clausesAreOrAndConditionsInsideClauseAreAnd() {
        DnfExpression<String> expression = DnfExpression.anyOf(
                DnfClause.allOf(value -> value.startsWith("A"), value -> value.endsWith("Z")),
                DnfClause.allOf(value -> value.equals("fallback")));

        assertTrue(expression.test("ABZ"));
        assertTrue(expression.test("fallback"));
        assertFalse(expression.test("AB"));
    }
}
