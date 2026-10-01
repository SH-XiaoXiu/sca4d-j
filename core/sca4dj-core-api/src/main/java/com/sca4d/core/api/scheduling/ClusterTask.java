package com.sca4d.core.api.scheduling;

import com.sca4d.core.api.job.Job;

/**
 * 表示由集群定时器直接执行的一项基础设施任务。
 *
 * <p>集群任务不创建 Job run、节点或执行结果；需要保存业务执行状态的动作必须使用
 * {@link Job}。
 */
@FunctionalInterface
public interface ClusterTask {

    /**
     * 执行一次当前任务。
     *
     * <p>调用方负责处理任务内部的幂等、租约和失败重试。抛出的运行时异常会结束本次触发，之后的触发仍会继续。
     */
    void execute();
}
