package com.sca4d.system.audit;

import lombok.Data;
import lombok.experimental.Accessors;

/** 操作日志中已经出现过的 Subject 操作人快照选项。 */
@Data
@Accessors(chain = true)
public class OperationLogOperatorOption {

    private Long subjectId;
    private String subjectDisplayName;
}
