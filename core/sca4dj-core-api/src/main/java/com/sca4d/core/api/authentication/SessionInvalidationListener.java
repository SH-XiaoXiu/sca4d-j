package com.sca4d.core.api.authentication;

/** 接收 Account Session 失效结果，并执行通知等不改变失效代次的操作。 */
public interface SessionInvalidationListener {

    /**
     * 在指定 Account 的 Session 失效代次增加后执行操作。
     *
     * @param accountId Session 已失效的 Account 标识
     */
    void invalidated(long accountId);
}
