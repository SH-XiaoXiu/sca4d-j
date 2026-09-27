package com.sca4d.core.api.scheduling;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneId;
import java.util.Objects;

/** 使用显式时区解释 Quartz Cron 表达式。 */
public class CronSchedule implements ScheduleTiming {

    private final @NotNull String expression;

    private final @NotNull ZoneId zone;

    /**
    * 创建 Cron 计划时间。
    *
     * @param expression 非空 Quartz Cron 表达式；具体语法由 JobScheduler 实现校验
     * @param zone 解释表达式使用的时区
     * @throws NullPointerException 表达式或时区为空时抛出
     * @throws IllegalArgumentException 表达式为空白文本时抛出
     */
    public CronSchedule(@NotNull String expression, @NotNull ZoneId zone) {
        this.expression = TextUtils.requireText(expression, "Cron 表达式");
        this.zone = Objects.requireNonNull(zone, "Cron 时区不能为空");
    }

    /**
     * 返回 Cron 表达式。
     *
     * @return 由 Scheduler 实现校验的 Quartz Cron 文本
     */
    @Contract(pure = true)
    public @NotNull String expression() {
        return expression;
    }

    /**
     * 返回 Cron 时区。
     *
     * @return 解释表达式使用的显式时区
     */
    @Contract(pure = true)
    public @NotNull ZoneId zone() {
        return zone;
    }
}
