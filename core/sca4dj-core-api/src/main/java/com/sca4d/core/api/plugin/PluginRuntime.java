package com.sca4d.core.api.plugin;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** 管理一个 App 的服务绑定、插件扩展与同步调用生命周期。 */
public interface PluginRuntime extends ApiInvoker, AutoCloseable {
    /** 在插件加载前登记公开契约。 */
    void declare(@NotNull ApiContract<?> contract);
    /** 在插件加载前绑定宿主服务，实际对象应为完成代理的 Bean。 */
    void bind(@NotNull ApiBinding binding);
    /**
     * 从本地普通 JAR 加载插件或替换同编码插件；必须在调用作用域外执行。
     * 候选初始化、版本校验或绑定冲突失败时抛出异常，已有代次继续接受调用。
     * 新代次发布后等待旧租用归还；等待超时或线程中断时，新代次仍然生效，旧代次保留 DRAINING 状态。
     *
     * @param artifact 插件构件路径；加载器使用构件副本，修改原文件不影响已加载代次
     * @param drainTimeout 非负等待时长；零表示不等待，已有调用仍可完成
     * @return 已发布的新代次及被替换的旧代次状态；首次加载没有旧代次
     */
    @NotNull PluginUpdate replace(@NotNull Path artifact, @NotNull Duration drainTimeout);
    /**
     * 关闭指定插件的新调用入口并等待已有调用；必须在调用作用域外执行。
     * 必需扩展不可被普通停用，停用失败时保留入口；等待超时不会强行终止代码。
     *
     * @param id 已启用插件的稳定编码
     * @param drainTimeout 非负等待时长；零表示只关闭入口并尝试清理
     * @return 停止、等待释放或释放失败的实际状态
     */
    @NotNull PluginStatus stop(@NotNull TreeCode id, @NotNull Duration drainTimeout);
    /** 固定当前线程的绑定快照，嵌套范围复用同一代次；返回的租用必须在原线程关闭。 */
    @NotNull PluginLease openScope();
    /** 检查必需扩展均已加载。 */
    void verifyRequired();
    /** 返回不持有实现对象的所有代次状态。 */
    @NotNull List<PluginStatus> statuses();
    /** 停止所有入口并释放无活动调用的代次；已有租用归还时再释放其代次，关闭后拒绝新作用域。 */
    @Override void close();
}
