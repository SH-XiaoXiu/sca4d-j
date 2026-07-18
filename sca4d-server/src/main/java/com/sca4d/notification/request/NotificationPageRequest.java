package com.sca4d.notification.request;

import com.sca4d.common.web.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 当前用户通知分页查询请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationPageRequest extends PageQuery {

    private Boolean unreadOnly;
}
