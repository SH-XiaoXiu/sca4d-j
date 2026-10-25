package com.sca4d.core.spring.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置一次 Redis pipeline 允许提交的最大命令数量。 */
@ConfigurationProperties("sca4dj.redis.pipeline")
public class RedisPipelineProperties {

    /** 配置键 `sca4dj.redis.pipeline.max-commands`，默认 `1000`，只能通过 setter 设置为正数。 */
    private int maxCommands = 1_000;

    public RedisPipelineProperties() {
    }

    /** @return 一次 Redis pipeline 最多包含的命令数量 */
    public int getMaxCommands() {
        return maxCommands;
    }

    /** @param maxCommands 一次 Redis pipeline 最多包含的正数命令数量 */
    public void setMaxCommands(int maxCommands) {
        if (maxCommands < 1) {
            throw new IllegalArgumentException("Redis pipeline 命令上限必须为正数");
        }
        this.maxCommands = maxCommands;
    }
}
