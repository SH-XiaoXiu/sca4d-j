package com.sca4d.core.api.scheduling;

/** 指定 Scheduler 错过原定时间后是否补一次执行。 */
public enum MisfirePolicy {
    /** 忽略已经错过的 fire，等待下一次正常时间。 */
    SKIP,
    /** 恢复后立即补一次执行，不逐个补齐全部错过时间。 */
    FIRE_ONCE_NOW
}
