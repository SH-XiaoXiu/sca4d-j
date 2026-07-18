package com.sca4d.system.request;

import com.sca4d.common.audit.AuditResult;
import com.sca4d.common.web.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * 操作日志分页查询请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogPageRequest extends PageQuery {

    private String moduleCode;
    private String actionCode;
    private AuditResult result;
    private Long subjectId;
    private Long principalId;
    private String targetType;
    private String targetId;
    private String traceId;
    private OffsetDateTime createdAtStart;
    private OffsetDateTime createdAtEnd;
}
