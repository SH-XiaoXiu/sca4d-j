package com.sca4d.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 请求基础配置。
 */
@ConfigurationProperties(prefix = "sca4dj.request")
public class Sca4djRequestProperties {

    private boolean contextEnabled = true;
    private int contextFilterOrder = -100;

    public boolean isContextEnabled() {
        return contextEnabled;
    }

    public void setContextEnabled(boolean contextEnabled) {
        this.contextEnabled = contextEnabled;
    }

    public int getContextFilterOrder() {
        return contextFilterOrder;
    }

    public void setContextFilterOrder(int contextFilterOrder) {
        this.contextFilterOrder = contextFilterOrder;
    }
}
