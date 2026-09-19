package com.sca4d.core.api.identity;

/** 系统任务执行、授权和审计使用的唯一 Principal。 */
public final class SystemPrincipal implements Principal {

    /** 唯一 SYSTEM Principal 实例。 */
    public static final SystemPrincipal INSTANCE = new SystemPrincipal();

    /** 固定 SYSTEM Principal 标识。 */
    public static final long ID = 1;

    private SystemPrincipal() {
    }

    /**
     * 返回固定的 SYSTEM Principal 标识。
     *
     * @return 固定的 SYSTEM Principal 标识
     */
    @Override
    public long principalId() {
        return ID;
    }

}
