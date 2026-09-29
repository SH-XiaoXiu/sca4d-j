package com.sca4d.core.api.call;

/** 表示业务方法成功前必须由调用过程满足的一项要求。 */
public interface CallRequirement {

    /** 校验执行前提；在身份选择后、权限和执行控制前调用，不应修改外部状态。 */
    default void verifyBeforeInvocation() {
    }

    /**
     * 校验当前要求已经满足。
     *
     * @throws IllegalStateException 调用过程没有满足要求时抛出
     */
    void verifySatisfied();

    /**
     * 判断只读事务提交前是否需要检查当前要求。
     *
     * @return 只读事务也会完成当前要求时返回 {@code true}
     */
    default boolean appliesToReadOnlyTransaction() {
        return true;
    }
}
