package com.sca4d.iam.credential;

import com.sca4d.common.auth.CredentialType;
import org.jetbrains.annotations.NotNull;

/** 绑定 Account 的认证材料，并验证一种指定 Java 类型 Proof。 */
public interface CredentialScheme<C, P> {

    /**
     * 返回本 Scheme 持久化时使用的 Credential 类型。
     *
     * @return 与认证材料 Java 类型唯一对应的持久化类型
     */
    @NotNull
    CredentialType credentialType();

    /**
     * 返回本 Scheme 接受的认证材料 Java 类型。
     *
     * <p>CredentialService 使用该类型建立唯一分派表；同一 Java 类型注册多个 Scheme 时应用启动失败。
     *
     * @return 认证材料的运行时类型
     */
    @NotNull
    Class<C> credentialClass();

    /**
     * 返回本 Scheme 接受的 Proof Java 类型。
     *
     * @return 验证指定 Account 时使用的 Proof 类型
     */
    @NotNull
    Class<P> proofClass();

    /**
     * 为 Account 新增一种尚未绑定的认证材料。
     *
     * @param accountId 认证材料所属 Account 主键
     * @param credential 由本 Scheme 负责转换、持久化和保护的认证材料
     * @throws com.sca4d.common.exception.BizException Account 不存在、材料不合法或唯一定位字段已被占用时抛出
     */
    @NotNull
    Long bind(@NotNull Long accountId, @NotNull C credential);

    /**
     * 替换 Account 已经绑定的同类认证材料。
     *
     * @param accountId 认证材料所属 Account 主键
     * @param credential 由本 Scheme 负责转换、持久化和保护的新认证材料
     * @throws com.sca4d.common.exception.BizException Account 或原绑定不存在、材料不合法或唯一定位字段冲突时抛出
     */
    void replace(
            @NotNull Long accountId,
            @NotNull Long credentialId,
            @NotNull Long version,
            @NotNull C credential);

    /**
     * 验证指定 Account 的 Proof。
     *
     * <p>AccountIdentifier、ExternalIdentity 或 Flow 先确定 Account，本方法只验证协议材料。实现不得创建
     * Session、选择 Principal 或加载权限。
     *
     * @param accountId 已由认证入口解析的 Account 主键；未知标识使用不存在的固定主键以执行等价验证
     * @param proof 当前认证操作提交的 Proof
     * @return 有效 Credential 当前绑定的 Account、Subject 和版本
     * @throws com.sca4d.common.auth.UnauthenticatedException Proof 错误或 Credential、Account、Subject 不可认证时抛出
     */
    @NotNull
    CredentialVerification verify(@NotNull Long accountId, @NotNull P proof);
}
