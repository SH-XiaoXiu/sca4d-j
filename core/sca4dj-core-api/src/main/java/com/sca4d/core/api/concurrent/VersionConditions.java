package com.sca4d.core.api.concurrent;

import org.jetbrains.annotations.NotNull;

/** 为一次根资源写入选择调用方提交的版本条件或 Repository 当前版本。 */
@FunctionalInterface
public interface VersionConditions {

    /**
     * 解析根资源写入条件。
     *
     * <p>HTTP `If-Match` 已绑定时消费该请求条件；普通 HTTP、消息和任务调用使用
     * Repository 已读取的当前版本。一个 HTTP `If-Match` 只能供一个根资源写入消费。
     *
     * @param currentVersion Repository 已读取的当前版本
     * @return 本次条件 SQL 使用的版本和零行失败含义
     * @throws IllegalStateException 同一个 `If-Match` 已经被另一个根资源写入消费时抛出
     */
    @NotNull
    VersionCondition resolve(@NotNull Version currentVersion);

    /**
     * 冻结当前调用的版本条件入口，供显式传入异步工作；默认无请求状态的实现保持自身。
     * 异步工作仍须使用自己的事务，不传播当前线程的事务或 CallHolder。
     *
     * @return 只消费当前请求条件一次的版本条件入口
     */
    default @NotNull VersionConditions capture() {
        return this;
    }
}
