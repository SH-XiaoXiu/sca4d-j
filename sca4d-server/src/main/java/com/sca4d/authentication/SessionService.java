package com.sca4d.authentication;

import com.sca4d.common.auth.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/** 创建 Account 登录 Session，并在 Subject、Principal 或授权变化后刷新已有 Session。 */
public interface SessionService {

    /**
     * 一次性装载 Account、Subject、可达 Principal 和权限并创建登录 Session。
     *
     * @param accountId 已完成认证的 Account 主键
     * @param deviceId 客户端设备标识
     * @param terminalName 面向用户展示的终端名称
     * @return 已写入完整认证授权数据的登录 Session
     */
    @NotNull
    Session create(
            @NotNull Long accountId,
            @Nullable String deviceId,
            @Nullable String terminalName);

    /**
     * 在当前事务提交后刷新相关 Subject 名下全部 Account Session。
     *
     * @param subjectIds 状态、Principal、角色或权限已经变化的 Subject 主键
     */
    void refreshAfterCommit(@NotNull Collection<Long> subjectIds);

    /**
     * 在当前事务提交后重装认证存储中全部在线 Account Session。
     *
     * <p>用于代码权限目录和默认授权等影响全部在线会话的启动同步，不扫描离线 Account。
     */
    void refreshOnlineAfterCommit();
}
