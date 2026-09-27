package com.sca4d.core.api.authentication;

import com.sca4d.core.api.utils.TextUtils;

import java.util.Objects;

/** 表示结束认证 Session 的开放原因。 */
public class SessionTerminationReason {

    /** 当前接入者主动请求注销 Session。 */
    public static final SessionTerminationReason USER_REQUESTED = new SessionTerminationReason("USER_REQUESTED");

    /** Session 对应的 Account、Subject 或业务身份已不能继续使用。 */
    public static final SessionTerminationReason IDENTITY_UNAVAILABLE = new SessionTerminationReason(
            "IDENTITY_UNAVAILABLE");

    /** Account 的认证策略或 Credential 已变化。 */
    public static final SessionTerminationReason AUTHENTICATION_CHANGED = new SessionTerminationReason(
            "AUTHENTICATION_CHANGED");

    private final String code;

    /**
     * 创建 Session 注销原因。
     *
     * @param code 非空原因编码
     */
    public SessionTerminationReason(String code) {
        this.code = TextUtils.requireText(code, "注销原因");
    }

    /**
     * 返回注销原因编码。
     *
     * @return 注销原因编码
     */
    public String code() {
        return code;
    }

    @Override
    public boolean equals(Object other) {
        return other == this || other instanceof SessionTerminationReason reason && code.equals(reason.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }
}
