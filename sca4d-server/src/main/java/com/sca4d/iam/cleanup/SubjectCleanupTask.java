package com.sca4d.iam.cleanup;

import lombok.Data;

/**
 * 集群节点已经原子领取的 Subject 关系清理任务。
 */
@Data
public class SubjectCleanupTask {

    private Long taskId;
    private Long subjectId;
}
