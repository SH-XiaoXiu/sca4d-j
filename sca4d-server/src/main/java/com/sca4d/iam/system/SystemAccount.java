package com.sca4d.iam.system;

/** 系统固定账号。 */
public final class SystemAccount {

    /** SYSTEM Subject 与 SYSTEM Principal 共用的固定主键。 */
    public static final Long ID = 1L;

    /** 禁止创建系统账号常量容器实例。 */
    private SystemAccount() {
    }
}
