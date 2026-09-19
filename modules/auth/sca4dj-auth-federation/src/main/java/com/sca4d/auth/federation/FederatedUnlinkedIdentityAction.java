package com.sca4d.auth.federation;

import com.sca4d.core.api.enums.BaseEnum;

/** 外部身份尚未绑定本地账号时的处理方式。 */
public enum FederatedUnlinkedIdentityAction implements BaseEnum {
    /** 终止登录并要求管理员或用户先完成绑定。 */
    REJECT,
    /** 在具体应用事务中创建 Platform Account。 */
    JIT_CREATE;

    /** @return 用于传输和持久化的策略值 */
    @Override
    public String value() {
        return name();
    }

    /** @return 策略的中文说明 */
    @Override
    public String description() {
        return this == REJECT ? "拒绝未绑定身份" : "自动创建账号";
    }
}
