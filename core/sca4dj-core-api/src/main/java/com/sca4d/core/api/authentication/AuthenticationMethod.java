package com.sca4d.core.api.authentication;

import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;

/**
 * 认证引擎可以发现并调用的一种认证方法；认证方法不判断登录、动作或因素组合策略。
 *
 * @param <P> 本方法接受的 Proof 类型
 */
public interface AuthenticationMethod<P> {

    /**
     * 读取配置、提交和证据共同使用的认证方法编码。
     *
     * @return 全局唯一且跨版本保持不变的认证方法编码
     */
    @NotNull
    TreeCode code();

    /**
     * 读取 Engine 在调用认证方法前执行精确类型检查所需的类型。
     *
     * @return 本方法接受的精确 Proof 类型
     */
    @NotNull
    Class<P> proofType();

    /**
     * 读取一次成功调用可能贡献的因素类别。
     *
     * @return 认证引擎计算剩余独立因素时使用的类别集合
     */
    @NotNull
    @Unmodifiable
    Set<AuthenticationFactorKind> factorKinds();

    /**
     * 判断当前认证方法是否可以从 Proof 中确定账号。
     *
     * @return 可以在尚未确定 Account 时执行认证则为 {@code true}
     */
    boolean canIdentifyAccount();

    /**
     * 判断指定 Account 当前能否使用本方法。
     *
     * @param account 已确定的 Account
     * @return 当前可用时为 {@code true}
     */
    boolean available(@NotNull Account account);

    /**
     * 验证 Proof 并返回可由 Engine 保存的成功证据。
     *
     * @param account 已确定的 Account；首次账号发现时为 {@code null}
     * @param proof   调用方提交且未持久化的 Proof
     * @return 成功验证结果
     */
    @NotNull
    VerifiedAuthentication authenticate(@Nullable Account account, @NotNull P proof);

    /**
     * 在完成动作认证前确认先前生成的证据仍然有效。
     *
     * @param account        证据所属 Account
     * @param authentication 先前生成且不包含秘密的验证结果
     */
    void verifyCurrent(@NotNull Account account, @NotNull VerifiedAuthentication authentication);

    /**
     * 永久删除指定 Account 由本方法负责的 Credential 和认证状态。
     *
     * <p>Account 删除流程可以重复调用当前方法；目标数据已经不存在时必须直接返回。实现不得删除其他认证方法或身份模块负责的数据。
     *
     * @param accountId 要清理的 Account 标识
     * @throws IllegalArgumentException Account 标识不是正数时抛出
     * @throws RuntimeException         持久化删除失败时抛出
     */
    void eraseAccount(long accountId);
}
