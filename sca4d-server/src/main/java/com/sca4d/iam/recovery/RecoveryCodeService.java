package com.sca4d.iam.recovery;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/** 为 Account 签发、撤销和查询恢复码集合，不记录或返回数据库摘要。 */
public interface RecoveryCodeService {

    /**
     * 替换 Account 当前恢复码集合。
     *
     * <p>调用方必须先完成 STEP_UP，并确保该方法与 Account 第二因子状态修改位于同一事务和 Subject 锁内。
     *
     * @param accountId 已确认属于当前 Subject 的 Account
     * @return 新恢复码和集合状态；明文只允许当前响应展示一次
     */
    @NotNull
    IssuedRecoveryCodes issue(@NotNull Long accountId);

    /**
     * 撤销 Account 当前恢复码并删除该集合的全部摘要。
     *
     * @param accountId 已关闭第二因子的 Account
     */
    void revoke(@NotNull Long accountId);

    /**
     * 批量读取 Account 当前恢复码集合状态。
     *
     * @param accountIds 去重前的 Account 主键
     * @return 只包含已签发当前集合的 Account
     */
    @NotNull
    Map<Long, RecoveryCodeSummary> findByAccounts(@NotNull Collection<Long> accountIds);

    /**
     * 校验并单次消费 Account 当前集合中的一条恢复码。
     *
     * <p>未知 Account、没有当前集合、格式错误、摘要不匹配和并发重复消费返回同一个认证失败结果。
     *
     * @param accountId 已由作用域内 Identifier 解析的 Account；未知账号使用固定不存在主键
     * @param code 用户提交且只存在于当前调用的恢复码
     * @return 当前仍可认证的 Subject、Account 和已使用恢复码版本
     */
    @NotNull
    RecoveryCodeVerification consume(@NotNull Long accountId, @NotNull String code);

    /**
     * 复核恢复码 Proof 引用的集合、Account 和 Subject 当前仍可用于恢复。
     *
     * @param verification Flow 保存的不含明文和摘要的恢复码验证结果
     * @return 使用当前数据库状态重建的验证结果
     */
    @NotNull
    RecoveryCodeVerification requireCurrent(@NotNull RecoveryCodeVerification verification);
}
