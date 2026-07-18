package com.sca4d.schedule.request;

import com.sca4d.common.web.PageQuery;
import com.sca4d.schedule.ScheduleStatus;
import com.sca4d.schedule.ScheduleType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Schedule 管理列表的分页和筛选请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SchedulePageRequest extends PageQuery {
    private String keyword;
    private ScheduleType scheduleType;
    private ScheduleStatus status;
    private String taskType;
}
