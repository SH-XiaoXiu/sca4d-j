package com.sca4d.core.api.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class DnfExpressionTest {

    @Test
    @DisplayName("Empty Clause Is True And Empty Expression Is False")
    void emptyClauseIsTrueAndEmptyExpressionIsFalse() {
        assertTrue(new DnfClause<String, Boolean>("always", List.of(), true).matches("value"));
        assertTrue(new DnfExpression<String, Boolean>(List.of()).evaluate("value").isEmpty());
    }

    @Test
    @DisplayName("Clauses Are Or And Conditions Inside Clause Are And")
    void clausesAreOrAndConditionsInsideClauseAreAnd() {
        DnfExpression<String, String> expression = DnfExpression.anyOf(
                DnfClause.allOf(
                        "starts-and-ends",
                        "matched",
                        value -> value.startsWith("A"),
                        value -> value.endsWith("Z")),
                DnfClause.allOf("fallback", "fallback", value -> value.equals("fallback")));

        assertEquals("matched", expression.evaluate("ABZ").orElseThrow());
        assertEquals("fallback", expression.evaluate("fallback").orElseThrow());
        assertTrue(expression.evaluate("AB").isEmpty());
    }
}
