package com.sca4d.core.api.flow;

import java.time.Duration;

/** 描述可创建 {@link FlowInstance} 的名称、版本、{@link FlowGraph} 和有效时间。 */
public interface FlowDefinition {

    /**
     * 返回定义名称。
     *
     * @return 定义名称
     */
    String name();

    /**
     * 返回正整数定义版本。
     *
     * @return 正整数定义版本
     */
    int version();

    /**
     * 返回不可变的 {@link FlowGraph}。
     *
     * @return 不可变的 {@link FlowGraph}
     */
    FlowGraph graph();

    /**
     * 返回实例从创建到过期的有效时间。
     *
     * @return 实例从创建到过期的有效时间
     */
    Duration timeToLive();
}
