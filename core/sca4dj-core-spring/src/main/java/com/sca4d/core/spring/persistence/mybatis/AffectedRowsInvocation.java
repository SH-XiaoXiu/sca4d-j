package com.sca4d.core.spring.persistence.mybatis;

import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存当前线程正在执行的 `@AffectedRows` Repository 声明及其唯一一条 DML 结果。 */
class AffectedRowsInvocation {

    private static final ThreadLocal<@Nullable AffectedRowsInvocation> CURRENT = new ThreadLocal<>();

    private final AffectedRows declaration;

    private boolean updateStarted;

    private @Nullable Integer affectedRows;

    /**
     * 保存声明并检查 target 不为空白。
     *
     * @param declaration Repository 方法上的 {@code @AffectedRows} 声明
     * @throws IllegalArgumentException target 为空白字符串时抛出
     */
    private AffectedRowsInvocation(AffectedRows declaration) {
        this.declaration = Objects.requireNonNull(declaration, "影响行数声明不能为空");
        if (declaration.target().isBlank()) {
            throw new IllegalArgumentException("@AffectedRows.target 不能为空白字符串");
        }
    }

    /**
     * 在当前线程登记 Repository 影响行数声明。
     *
     * @param declaration 当前 Repository 方法的声明
     * @throws IllegalStateException 当前线程已有未结束的声明时抛出，即 @AffectedRows 方法嵌套调用
     */
    static void open(AffectedRows declaration) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("@AffectedRows Repository 方法不能嵌套调用");
        }
        CURRENT.set(new AffectedRowsInvocation(declaration));
    }

    /** @return 当前声明；当前线程没有执行声明时返回 {@code null} */
    static @Nullable AffectedRowsInvocation currentOrNull() {
        return CURRENT.get();
    }

    /**
     * 记录一条即将执行的 DML。
     *
     * @throws IllegalStateException 当前声明已经执行过一条 DML 时抛出
     */
    void beginUpdate() {
        if (updateStarted) {
            throw new IllegalStateException(
                    "@AffectedRows Repository 方法只能执行一条 DML: " + declaration.target());
        }
        updateStarted = true;
    }

    /** 保存 MyBatis 执行器返回的真实影响行数。 */
    void recordAffectedRows(int actual) {
        affectedRows = actual;
    }

    /**
     * 检查方法已经执行一条 DML，并按照 Repository 声明解释其影响行数。
     *
     * @throws IllegalStateException 方法没有执行 DML 或 MyBatis 没有返回影响行数时抛出
     */
    void complete() {
        if (!updateStarted) {
            throw new IllegalStateException(
                    "@AffectedRows Repository 方法必须执行一条 DML: " + declaration.target());
        }
        Integer actual = affectedRows;
        if (actual == null) {
            throw new IllegalStateException(
                    "MyBatis 没有返回 DML 影响行数: " + declaration.target());
        }
        AffectedRowCounts.requireSingle(actual, declaration.whenZero(), declaration.target());
    }

    /** 清理当前线程的 Repository 影响行数声明。 */
    static void clear() {
        CURRENT.remove();
    }
}
