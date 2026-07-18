package com.sca4d.system.request;

import com.sca4d.common.web.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 操作日志历史操作人远程选择查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogOperatorOptionsRequest extends PageQuery {

    private String search;
}
