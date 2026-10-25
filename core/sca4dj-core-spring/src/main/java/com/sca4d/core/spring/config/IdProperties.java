package com.sca4d.core.spring.config;

import com.sca4d.core.spring.id.SnowflakeIdGenerator;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置默认雪花 ID 生成器使用的运行节点。 */
@ConfigurationProperties("sca4dj.id")
public class IdProperties {

    /** 当前进程的雪花 ID 节点编号，默认 0，范围 0 至 `SnowflakeIdGenerator.MAX_NODE_ID`。 */
    private long nodeId;

    /** 创建使用本地单节点编号 0 的配置。 */
    public IdProperties() {
    }

    /**
     * 返回当前进程的雪花节点编号。
     *
     * @return 范围为 0 至 1023 的节点编号
     */
    public long getNodeId() {
        return nodeId;
    }

    /**
     * 设置当前进程的雪花节点编号。
     *
     * @param nodeId 范围为 0 至 1023 的节点编号
     */
    public void setNodeId(long nodeId) {
        if (nodeId < 0 || nodeId > SnowflakeIdGenerator.MAX_NODE_ID) {
            throw new IllegalArgumentException("雪花 ID 节点编号必须位于 0 至 1023 之间");
        }
        this.nodeId = nodeId;
    }
}
