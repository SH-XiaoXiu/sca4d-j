package com.sca4d.config;

import com.sca4d.schedule.ScheduleTask;
import com.sca4d.schedule.ScheduleTaskRegistry;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** ScheduleTask 扩展目录配置。 */
@Configuration
public class ScheduleConfig {

    /**
     * 汇总并校验应用提供的全部任务类型。
     *
     * @param tasks Spring 容器中的 ScheduleTask 实现
     * @return 按固定 taskType 建立的唯一注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull ScheduleTaskRegistry scheduleTaskRegistry(@NotNull List<ScheduleTask<?>> tasks) {
        return new ScheduleTaskRegistry(tasks);
    }
}
