package com.sca4d.file;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

/** 验证文件清理 Job 按批次删除到期文件并记录失败。 */
class FileCleanupJobTest {

    @Test
    void shouldDelegateScheduledCleanupToFileService() {
        FileService fileService = mock(FileService.class);

        new FileCleanupJob(fileService).cleanupFiles();

        verify(fileService).cleanup();
    }
}
