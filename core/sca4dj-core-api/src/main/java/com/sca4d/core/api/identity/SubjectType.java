package com.sca4d.core.api.identity;

import com.sca4d.core.api.enums.BaseEnum;

/** 表示实际接入系统并完成认证的主体类型。 */
public enum SubjectType implements BaseEnum {
    /** 使用密码、Passkey 或其他自然人认证方式接入的人。 */
    HUMAN("HUMAN", "自然人"),
    /** 使用设备凭据或设备协议接入的设备。 */
    DEVICE("DEVICE", "设备"),
    /** 使用应用密钥、请求签名或其他程序认证方式接入的应用。 */
    APPLICATION("APPLICATION", "应用"),
    /** 由当前系统直接建立且不对应外部认证者的系统执行器。 */
    SYSTEM("SYSTEM", "系统");

    private final String value;

    private final String description;

    SubjectType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * 返回用于传输和持久化的固定类型值。
     *
     * @return 固定类型值
     */
    @Override
    public String value() {
        return value;
    }

    /**
     * 返回主体类型的中文说明。
     *
     * @return 中文说明
     */
    @Override
    public String description() {
        return description;
    }
}
