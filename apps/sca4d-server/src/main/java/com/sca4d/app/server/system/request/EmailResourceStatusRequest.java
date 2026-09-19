package com.sca4d.app.server.system.request;

/** 接收 SMTP 配置或 Email 模板的新启用状态。 */
public final class EmailResourceStatusRequest {

    private boolean enabled;

    /** 创建供 JSON 反序列化使用的空请求。 */
    public EmailResourceStatusRequest() {
    }

    /** @return 资源是否参与实际通知投递 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 资源是否参与实际通知投递 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
