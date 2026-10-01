package com.sca4d.core.api.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    @Test
    @DisplayName("异步条件拒绝同步入口，完整并行判断等待所有条件")
    void asyncConditionsRequireAsyncEntryAndWaitForAllResults() {
        CompletableFuture<Boolean> pending = new CompletableFuture<>();
        DnfClause.Condition<String> remote = DnfClause.Condition.async((value, executor) -> pending);
        DnfClause<String, Boolean> clause = DnfClause.allOf(
                "parallel",
                true,
                remote,
                value -> false);

        assertThrows(IllegalStateException.class, () -> clause.matches("value"));
        var result = clause.matchesAsync("value", Runnable::run).toCompletableFuture();
        assertFalse(result.isDone());
        pending.complete(true);
        assertFalse(result.join());
    }
}
