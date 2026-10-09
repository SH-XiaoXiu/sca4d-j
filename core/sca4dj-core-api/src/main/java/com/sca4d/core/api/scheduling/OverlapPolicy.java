package com.sca4d.core.api.scheduling;

/** 指定同一计划已有活动 run 时怎样处理新的 fire。 */
public enum OverlapPolicy {
    /** 跳过新的 fire，不创建排队 run。 */
    SKIP_IF_RUNNING,
    /** 保存新的运行，等同一工作键下更早的运行结束后再执行。 */
    WAIT,
    /** 每个 fire 都创建独立 run。 */
    ALLOW
}
