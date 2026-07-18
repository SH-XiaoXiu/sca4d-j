package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.audit.AuditResult;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * 系统操作日志实体。
 */
@Data
@Accessors(chain = true)
@Table("sys_log")
public class SysOperationLogEntity {

    private Long id;
    private Long principalId;
    private Long subjectId;
    private Long accountId;
    private String subjectDisplayName;
    private String principalDisplayName;
    private String moduleCode;
    private String actionCode;
    private AuditResult result;
    private String requestUri;
    private String targetType;
    private String targetId;
    private String detailJson;
    private OffsetDateTime createdAt;
}
