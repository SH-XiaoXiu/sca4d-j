package com.sca4d.core.api.plugin;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/** 由 App 读取插件声明的元信息、映射运行描述，并管理每个代次的公开配置。 */
public interface PluginConfiguration<C> {
    /** 返回 App 向插件公开的配置类型，必须来自 App API 或明确共享的构件。 */
    @NotNull
    Class<C> type();

    /**
     * 为新构件读取元信息及额外配置；字段、格式和配置来源由 App 定义。
     * 返回值归候选代次所有，不得修改活动代次的数据；返回前失败时自行释放已创建的资源。
     *
     * @param artifact 运行时固定的 JAR 副本，只能读取，不得修改或删除
     * @return 运行描述与不可变公开配置；运行时随后校验 API 并初始化插件
     * @throws Exception 读取、映射或配置校验失败，活动代次保持不变
     */
    @NotNull
    PluginConfigurationSnapshot<C> load(@NotNull Path artifact) throws Exception;

    /**
     * 从活动构件的副本重新读取配置，为新实例创建独立快照；不得修改已有快照。
     * 返回的插件编码必须与活动代次一致，返回前失败时自行释放已创建的资源。
     *
     * @param artifact 活动代次保存的构件副本，不会重新读取部署目录中的原文件
     * @return 新代次使用的描述与不可变公开配置
     * @throws Exception 配置重载失败，活动代次保持不变
     */
    @NotNull
    PluginConfigurationSnapshot<C> reload(@NotNull Path artifact) throws Exception;

    /**
     * 释放快照拥有的资源；候选失败或代次无活动租用后，每个快照只调用一次。
     * 可能与另一个代次的加载同时执行，不得释放其他快照或 App 共享服务。
     *
     * @param snapshot 已结束使用的配置快照
     * @throws Exception 资源释放失败，运行时仍尝试关闭插件加载器并报告实际失败
     */
    void unload(@NotNull PluginConfigurationSnapshot<C> snapshot) throws Exception;
}
