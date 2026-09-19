package com.sca4d.core.api.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.concurrent.OptimisticLockException;
import com.sca4d.core.api.concurrent.VersionPreconditionFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 Repository 影响行数的统一解释规则。 */
class AffectedRowCountsTest {

    @Test
    @DisplayName("单行写入接受一行并按策略解释零行")
    void interpretsSingleRowWrites() {
        assertDoesNotThrow(() -> AffectedRowCounts.requireSingle(1, ZeroRowPolicy.ERROR, "设置"));
        assertDoesNotThrow(() -> AffectedRowCounts.requireSingle(0, ZeroRowPolicy.ALLOW, "设置"));
        assertThrows(
                AffectedRowsMismatchException.class,
                () -> AffectedRowCounts.requireSingle(0, ZeroRowPolicy.ERROR, "设置"));
        assertThrows(
                OptimisticLockException.class,
                () -> AffectedRowCounts.requireSingle(0, ZeroRowPolicy.OPTIMISTIC_LOCK, "设置"));
        assertThrows(
                VersionPreconditionFailedException.class,
                () -> AffectedRowCounts.requireSingle(0, ZeroRowPolicy.VERSION_PRECONDITION, "设置"));
    }

    @Test
    @DisplayName("所有单行策略都拒绝多行和负数结果")
    void rejectsInvalidSingleRowCounts() {
        for (ZeroRowPolicy policy : ZeroRowPolicy.values()) {
            AffectedRowsMismatchException multiple = assertThrows(
                    AffectedRowsMismatchException.class,
                    () -> AffectedRowCounts.requireSingle(2, policy, "设置"));
            assertEquals(2, multiple.actual());
            assertThrows(
                    AffectedRowsMismatchException.class,
                    () -> AffectedRowCounts.requireSingle(-1, policy, "设置"));
        }
    }

    @Test
    @DisplayName("动态预期行数必须与数据库结果相同")
    void requiresExactDynamicCount() {
        assertDoesNotThrow(() -> AffectedRowCounts.requireExactly(3, 3, "批量设置"));
        AffectedRowsMismatchException exception = assertThrows(
                AffectedRowsMismatchException.class,
                () -> AffectedRowCounts.requireExactly(2, 3, "批量设置"));

        assertEquals("批量设置", exception.target());
        assertEquals(3, exception.minimum());
        assertEquals(3, exception.maximum());
        assertEquals(2, exception.actual());
        assertThrows(IllegalArgumentException.class, () -> AffectedRowCounts.requireExactly(0, -1, "设置"));
    }

    @Test
    @DisplayName("动态写入可以要求最小行数")
    void requiresMinimumDynamicCount() {
        assertDoesNotThrow(() -> AffectedRowCounts.requireAtLeast(3, 1, "账号关系"));
        AffectedRowsMismatchException exception = assertThrows(
                AffectedRowsMismatchException.class,
                () -> AffectedRowCounts.requireAtLeast(0, 1, "账号关系"));

        assertEquals(1, exception.minimum());
        assertEquals(Integer.MAX_VALUE, exception.maximum());
        assertEquals("账号关系要求至少影响 1 行，实际影响 0 行", exception.getMessage());
        assertThrows(IllegalArgumentException.class, () -> AffectedRowCounts.requireAtLeast(0, -1, "账号关系"));
    }

    @Test
    @DisplayName("可选单行写入只把零行和一行转换成布尔结果")
    void convertsOptionalSingleRowWrite() {
        assertFalse(AffectedRowCounts.changed(0, "设置"));
        assertTrue(AffectedRowCounts.changed(1, "设置"));
        assertThrows(AffectedRowsMismatchException.class, () -> AffectedRowCounts.changed(2, "设置"));
    }

    @Test
    @DisplayName("任意行写入只拒绝负数并转换是否发生修改")
    void convertsArbitraryRowWrite() {
        assertFalse(AffectedRowCounts.any(0, "撤销凭据"));
        assertTrue(AffectedRowCounts.any(2, "撤销凭据"));
        assertThrows(AffectedRowsMismatchException.class, () -> AffectedRowCounts.any(-1, "撤销凭据"));
    }

    @Test
    @DisplayName("所有入口拒绝空白目标")
    void rejectsBlankTarget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AffectedRowCounts.requireSingle(1, ZeroRowPolicy.ERROR, " "));
        assertThrows(IllegalArgumentException.class, () -> AffectedRowCounts.requireExactly(1, 1, ""));
        assertThrows(IllegalArgumentException.class, () -> AffectedRowCounts.requireAtLeast(1, 0, "\n"));
        assertThrows(IllegalArgumentException.class, () -> AffectedRowCounts.changed(0, "\t"));
        assertThrows(IllegalArgumentException.class, () -> AffectedRowCounts.any(0, " "));
    }
}
