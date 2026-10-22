package com.sca4d.core.api.scheduling;

/** JobSchedule 只接受 Cron 或固定间隔两种时间定义。 */
public sealed interface ScheduleTiming permits CronSchedule, IntervalSchedule {
}
