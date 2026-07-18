package com.sca4d.common.auth;

import com.sca4d.common.permission.SessionPermission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 读取、创建和结束已认证 Subject 的登录会话。
 *
 * <p>该接口不校验 Credential、不选择 Principal，也不判断权限。具体 token、终端、多设备和存储由认证模块实现。
 */
public interface Authentication {

    /**
     * 使用已认证 Subject 和 Account 创建会话。
     *
     * @param subject 已认证 Subject
     * @param account 已完成认证且固定于新会话的 Account
     * @param principals 当前可选择的全部 Principal
     * @param permissions 每个 Principal 的角色和权限
     * @return 已创建的会话
     */
    @NotNull
    Session createSession(
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions);

    /**
     * 使用已认证 Subject、Account 和设备信息创建会话。
     *
     * @param subject 已认证 Subject
     * @param account 已完成认证且固定于新会话的 Account
     * @param principals 当前可选择的全部 Principal
     * @param permissions 每个 Principal 的角色和权限
     * @param deviceId 设备标识
     * @param terminalName 终端名称
     * @return 已创建的会话
     */
    @NotNull
    Session createSession(
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions,
            @Nullable String deviceId,
            @Nullable String terminalName);

    /**
     * 替换指定 Account 的 Sa-Token Account-Session 内容。
     *
     * @param accountId Account 主键
     * @param subject 已从当前 IAM 数据重新装载的 Subject
     * @param account 已从当前 IAM 数据重新装载的 Account
     * @param principals 当前可选择的全部 Principal
     * @param permissions 每个 Principal 的角色和权限
     */
    void replaceSession(
            @NotNull Long accountId,
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull List<Principal> principals,
            @NotNull Map<Long, SessionPermission> permissions);

    /**
     * 读取当前认证存储中仍有 Account-Session 的在线 Account。
     *
     * @return 去重且顺序固定的在线 Account 主键
     */
    @NotNull
    List<Long> onlineAccountIds();

    /**
     * 结束当前请求关联的已认证会话。
     *
     * <p>认证实现负责将项目会话映射到具体 token 框架的退出或失效操作；调用方不直接操作 {@link Session}。
     *
     * @throws UnauthenticatedException 当前请求不存在有效会话时抛出
     */
    void logout();

    /**
     * 读取当前请求会话。
     *
     * @return 当前会话；未登录时返回 null
     */
    @Nullable
    Session currentSession();

    /**
     * 结束指定 Account 的全部登录会话。
     *
     * <p>实现应调用认证框架的账号级会话终止 API，不为终止操作逐个加载完整会话详情。
     *
     * @param accountId Account 主键
     */
    void endSessions(@NotNull Long accountId);

    /**
     * 列出指定 Account 当前存在的安全会话快照。
     *
     * <p>实现不得返回 Token，也不得为每个终端逐项读取外部存储。
     *
     * @param accountId Account 主键
     * @return 按登录时间倒序排列的活跃会话
     */
    @NotNull
    List<SessionSnapshot> sessions(@NotNull Long accountId);

    /**
     * 结束指定 Account 下的一个项目会话。
     *
     * @param accountId Session 所属 Account 主键
     * @param sessionId 不能用于认证的项目会话标识
     * @throws com.sca4d.common.exception.BizException 会话不存在或不属于目标 Subject 时抛出
     */
    void endSession(@NotNull Long accountId, @NotNull String sessionId);
}
