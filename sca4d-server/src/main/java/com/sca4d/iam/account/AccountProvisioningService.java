package com.sca4d.iam.account;

import com.sca4d.common.auth.AccountScopeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 创建新 Subject 的首个 Account、DEFAULT Principal 和 DEFAULT Profile。 */
public interface AccountProvisioningService {

    /**
     * 创建带用户名和 Credential 的新 Account。
     *
     * @param username 首个 USERNAME Identifier
     * @param credential 首个认证材料
     * @param scopeType Account 作用域类型
     * @param scopeId TENANT Account 所属租户；GLOBAL Account 为 {@code null}
     * @param displayName Subject 默认展示名
     * @param phone 初始 PHONE Contact；缺失时不写入
     * @param email 初始 EMAIL Contact；缺失时不写入
     * @param avatarFileId 初始 DEFAULT Profile 头像文件；缺失时不绑定
     * @param avatarUploadGrant 头像上传授权；未绑定头像时不提供
     * @param actorPrincipalId 上传头像的 Principal；未绑定头像时不提供
     * @return 已写入的 Subject、DEFAULT Principal 和 Account
     */
    <C> @NotNull ProvisionedAccount create(
            @NotNull String username,
            @NotNull C credential,
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId,
            @NotNull String displayName,
            @Nullable String phone,
            @Nullable String email,
            @Nullable Long avatarFileId,
            @Nullable String avatarUploadGrant,
            @Nullable Long actorPrincipalId);

    /**
     * 创建仅供 ExternalIdentity 绑定的 Account。
     *
     * <p>该方法不创建 PLATFORM Principal。TENANT 访问关系由调用方依据身份提供方的明确租户策略建立。
     *
     * @param scopeType Account 作用域类型
     * @param scopeId TENANT Account 所属租户；GLOBAL Account 为 {@code null}
     * @param displayName Subject 和 DEFAULT Principal 使用的展示名
     * @return 已写入的 Subject、DEFAULT Principal 和 Account
     */
    @NotNull
    ProvisionedAccount createExternal(
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId,
            @NotNull String displayName);
}
