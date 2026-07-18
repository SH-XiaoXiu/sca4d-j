package com.sca4d.common.audit;

import org.jetbrains.annotations.NotNull;

/** 将一次完整审计数据持久化到项目选定审计介质的扩展点。 */
public interface AuditRecorder {

    /**
     * 持久化一次已经采集完成的审计记录。
     *
     * <p>实现负责生成缺失的持久化标识并隔离自己的事务。抛出的运行时异常由审计切面记录，不得改变原业务结果。
     *
     * @param record 包含操作者、请求、目标、结果和载荷的不可跨调用复用快照
     */
    void record(@NotNull AuditRecord record);
}
