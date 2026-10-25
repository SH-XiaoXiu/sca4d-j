package com.sca4d.core.api.persistence;

import com.sca4d.core.api.concurrent.exception.OptimisticLockException;
import com.sca4d.core.api.concurrent.exception.VersionPreconditionFailedException;
import com.sca4d.core.api.persistence.exception.AffectedRowsMismatchException;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 解释 Repository 从 Mapper 得到的影响行数，并拒绝超出声明范围的写入结果。 */
public class AffectedRowCounts {

    private AffectedRowCounts() {
    }

    /**
     * 检查单行写入，并按声明解释零行结果。
     *
     * @param actual 数据库返回的实际影响行数
     * @param whenZero 零行解释方式
     * @param target 发生写入的持久化对象说明
     * @throws AffectedRowsMismatchException 行数不符合声明范围时抛出
     * @throws OptimisticLockException 零行表示内部乐观版本冲突时抛出
     * @throws VersionPreconditionFailedException 零行表示调用方版本前置条件失败时抛出
     */
    @Contract(pure = true)
    public static void requireSingle(
            int actual,
            @NotNull ZeroRowPolicy whenZero,
            @NotNull String target) {
        ZeroRowPolicy checkedPolicy = Objects.requireNonNull(whenZero, "零行解释方式不能为空");
        String checkedTarget = requireTarget(target);
        if (actual == 1 || actual == 0 && checkedPolicy == ZeroRowPolicy.ALLOW) {
            return;
        }
        if (actual != 0) {
            int minimum = checkedPolicy == ZeroRowPolicy.ALLOW ? 0 : 1;
            throw new AffectedRowsMismatchException(checkedTarget, minimum, 1, actual);
        }
        switch (checkedPolicy) {
            case ERROR -> throw new AffectedRowsMismatchException(checkedTarget, 1, 1, 0);
            case ALLOW -> {
                return;
            }
            case OPTIMISTIC_LOCK -> throw new OptimisticLockException(checkedTarget);
            case VERSION_PRECONDITION -> throw new VersionPreconditionFailedException(checkedTarget);
        }
    }

    /**
     * 检查写入是否准确影响调用方给出的行数。
     *
     * @param actual 数据库返回的实际影响行数
     * @param expected 调用方根据集合大小或稳定协议确定的预期行数
     * @param target 发生写入的持久化对象说明
     * @throws IllegalArgumentException expected 小于零时抛出
     * @throws AffectedRowsMismatchException 实际行数不等于预期行数时抛出
     */
    @Contract(pure = true)
    public static void requireExactly(
            int actual,
            int expected,
            @NotNull String target) {
        if (expected < 0) {
            throw new IllegalArgumentException("预期影响行数不能小于零");
        }
        String checkedTarget = requireTarget(target);
        if (actual != expected) {
            throw new AffectedRowsMismatchException(checkedTarget, expected, expected, actual);
        }
    }

    /**
     * 检查写入是否至少影响调用方给出的行数。
     *
     * @param actual 数据库返回的实际影响行数
     * @param minimum 调用方根据持久化约束确定的最小行数
     * @param target 发生写入的持久化对象说明
     * @throws IllegalArgumentException minimum 小于零时抛出
     * @throws AffectedRowsMismatchException 实际行数小于最小行数时抛出
     */
    @Contract(pure = true)
    public static void requireAtLeast(
            int actual,
            int minimum,
            @NotNull String target) {
        if (minimum < 0) {
            throw new IllegalArgumentException("最小影响行数不能小于零");
        }
        String checkedTarget = requireTarget(target);
        if (actual < minimum) {
            throw new AffectedRowsMismatchException(checkedTarget, minimum, Integer.MAX_VALUE, actual);
        }
    }

    /**
     * 把最多影响一行的条件写入转换为是否发生修改。
     *
     * @param actual 数据库返回的实际影响行数
     * @param target 发生写入的持久化对象说明
     * @return 影响一行时为 {@code true}，没有影响行时为 {@code false}
     * @throws AffectedRowsMismatchException 实际影响多行或返回负数时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static boolean changed(int actual, @NotNull String target) {
        requireSingle(actual, ZeroRowPolicy.ALLOW, target);
        return actual == 1;
    }

    /**
     * 把允许影响任意行的写入转换为是否至少修改一行。
     *
     * @param actual 数据库返回的实际影响行数
     * @param target 发生写入的持久化对象说明
     * @return 至少影响一行时为 {@code true}，没有影响行时为 {@code false}
     * @throws AffectedRowsMismatchException 实际影响行数为负数时抛出
     */
    @CheckReturnValue
    @Contract(pure = true)
    public static boolean any(int actual, @NotNull String target) {
        requireAtLeast(actual, 0, target);
        return actual > 0;
    }

    /** 检查持久化对象说明，避免生成无法定位写入位置的异常。 */
    private static @NotNull String requireTarget(@NotNull String target) {
        String checked = Objects.requireNonNull(target, "影响行数检查对象不能为空");
        if (checked.isBlank()) {
            throw new IllegalArgumentException("影响行数检查对象不能为空白字符串");
        }
        return checked;
    }
}
