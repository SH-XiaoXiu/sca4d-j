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
}
