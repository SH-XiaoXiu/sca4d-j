package com.sca4d.common.access;

import com.sca4d.common.auth.ForbiddenException;

/** 资源访问策略缺失、拒绝或未被正确消费时使用的失败关闭异常。 */
public class ResourceAccessDeniedException extends ForbiddenException {

    /** 创建使用共用固定消息键的资源访问拒绝异常。 */
    public ResourceAccessDeniedException() {
        super();
    }
}
