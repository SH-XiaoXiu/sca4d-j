package com.sca4d.core.api.connection;

import com.sca4d.core.api.authentication.SessionId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.List;

/** 登记、续租和按 Session 关闭 App 持续连接的公共边界。 */
public interface ConnectionRegistry {

    /**
     * 登记一条新的连接信息；相同连接标识再次登记时由实现刷新当前信息。
     *
     * @param connection 已建立的连接信息
     */
    void register(@NotNull Connection connection);

    /**
     * 更新连接最近活动时间，供协议心跳或业务活动续租共享事实。
     *
     * @param connectionId 待更新的连接标识
     * @param lastActivityAt 本次活动发生的时间
     */
    void touch(@NotNull ConnectionId connectionId, @NotNull Instant lastActivityAt);

    /**
     * 删除连接信息；连接不存在和重复删除都必须幂等。
     *
     * @param connectionId 待删除的连接标识
     */
    void remove(@NotNull ConnectionId connectionId);

    /**
     * 返回当前 Session 关联的连接快照。
     *
     * @param sessionId Session 标识
     * @return 当前仍在注册表中的不可修改连接列表；没有连接时为空列表
     */
    @NotNull
    @Unmodifiable
    List<Connection> findBySession(@NotNull SessionId sessionId);

    /**
     * 终止当前 Session 关联的全部连接。
     *
     * <p>实现至少删除共享连接信息；如果实现能够定位协议句柄，还必须把稳定原因传给持有句柄的节点并请求断开。
     * 调用应幂等，已不存在的连接不构成失败。</p>
     *
     * @param sessionId Session 标识
     * @param reason 不包含凭据和隐私数据的稳定终止原因
     */
    void terminateBySession(@NotNull SessionId sessionId, @NotNull String reason);
}
