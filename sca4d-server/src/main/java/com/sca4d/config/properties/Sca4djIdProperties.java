package com.sca4d.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 集群 ID 配置。
 */
@ConfigurationProperties(prefix = "sca4dj.id")
public class Sca4djIdProperties {

    private long workerId = 1;
    private long dataCenterId = 1;

    public long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(long workerId) {
        this.workerId = workerId;
    }

    public long getDataCenterId() {
        return dataCenterId;
    }

    public void setDataCenterId(long dataCenterId) {
        this.dataCenterId = dataCenterId;
    }
}
