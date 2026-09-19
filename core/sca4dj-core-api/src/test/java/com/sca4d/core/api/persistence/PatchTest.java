package com.sca4d.core.api.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 验证 Patch 三种持久化意图、回调解析和条件 DSL。 */
class PatchTest {

    @Test
    @DisplayName("IGNORE、SET 和 CLEAR 保持互斥状态")
    void keepsStatesExclusive() {
        Patch<String> ignore = Patch.ignore();
        Patch<String> set = Patch.set("remark");
        Patch<String> clear = Patch.clear();

        assertTrue(ignore.isIgnore());
        assertFalse(ignore.isSet());
        assertFalse(ignore.isClear());
        assertTrue(set.isSet());
        assertEquals("remark", set.value());
        assertEquals("remark", set.getValue());
        assertTrue(clear.isClear());
        assertThrows(IllegalStateException.class, ignore::value);
        assertThrows(IllegalStateException.class, clear::getValue);
    }

    @Test
    @DisplayName("SET 接受零、false、空字符串和空集合")
    void keepsFalsyAndEmptyValues() {
        assertEquals(0, Patch.set(0).value());
        assertFalse(Patch.set(false).value());
        assertEquals("", Patch.set("").value());
        assertEquals(List.of(), Patch.set(List.of()).value());
    }

    @Test
    @DisplayName("SET 拒绝 null 并由 CLEAR 单独表示数据库 NULL")
    void rejectsNullSetValue() {
        assertThrows(NullPointerException.class, () -> Patch.set(null));
        assertDoesNotThrow(() -> Patch.<String>clear());
    }

    @Test
    @DisplayName("resolve 把可空业务结果交给回调明确决定")
    void resolvesNullableSource() {
        Patch<String> resolved = Patch.resolve(
                (String) null,
                value -> value == null ? Patch.clear() : Patch.set(value));

        assertTrue(resolved.isClear());
        assertThrows(NullPointerException.class, () -> Patch.resolve("value", ignored -> null));
        assertThrows(NullPointerException.class, () -> Patch.resolve("value", null));
    }

    @Test
    @DisplayName("choose 的直接 SET 入口拒绝 null")
    void rejectsNullDirectValues() {
        assertThrows(NullPointerException.class, () -> Patch.<String>choose().setIf(false, (String) null));
        assertThrows(NullPointerException.class, () -> Patch.<String>choose().otherwiseSet((String) null));
    }

    @Test
    @DisplayName("choose 返回唯一命中分支并使用显式兜底")
    void selectsOneBranchOrExplicitFallback() {
        Patch<String> selected = Patch.<String>choose()
                .setIf(false, "unused")
                .setIf(true, "selected")
                .clearIf(false)
                .otherwiseIgnore();
        Patch<String> ignored = Patch.<String>choose().setIf(false, "unused").otherwiseIgnore();
        Patch<String> cleared = Patch.<String>choose().clearIf(false).otherwiseClear();
        Patch<String> fallback = Patch.<String>choose().otherwiseSet("fallback");

        assertEquals(Patch.set("selected"), selected);
        assertSame(Patch.ignore(), ignored);
        assertSame(Patch.clear(), cleared);
        assertEquals("fallback", fallback.value());
    }

    @Test
    @DisplayName("choose 拒绝两个条件同时命中")
    void rejectsAmbiguousConditions() {
        Patch.Choice<String> choice = Patch.<String>choose().setIf(true, "first");

        assertThrows(IllegalStateException.class, () -> choice.clearIf(true));
    }

    @Test
    @DisplayName("choose 只执行最终选中的 Supplier")
    void evaluatesSuppliersLazily() {
        AtomicInteger selectedCalls = new AtomicInteger();
        AtomicInteger ignoredCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();

        Patch<String> patch = Patch.<String>choose()
                .setIf(false, () -> {
                    ignoredCalls.incrementAndGet();
                    return "ignored";
                })
                .setIf(true, () -> {
                    selectedCalls.incrementAndGet();
                    return "selected";
                })
                .otherwiseSet(() -> {
                    fallbackCalls.incrementAndGet();
                    return "fallback";
                });

        assertEquals("selected", patch.value());
        assertEquals(1, selectedCalls.get());
        assertEquals(0, ignoredCalls.get());
        assertEquals(0, fallbackCalls.get());
    }
}
