package com.sca4d.file;

import lombok.RequiredArgsConstructor;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * 文件过期与删除清理的当前调度实现。
 */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class FileCleanupJob implements Job {

    private final FileService fileService;

    /**
     * 按配置周期触发一轮可重入文件清理。
     */
    public void cleanupFiles() {
        fileService.cleanup();
    }

    /** @param context Quartz 单次触发上下文 */
    @Override
    public void execute(JobExecutionContext context) {
        cleanupFiles();
    }
}
