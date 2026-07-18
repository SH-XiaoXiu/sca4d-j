package com.sca4d.iam.recovery;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 新签发且只允许当前响应展示一次的恢复码及其非敏感集合状态。 */
public class IssuedRecoveryCodes {

    private final RecoveryCodeSummary summary;
    private final List<String> codes;

    /**
     * 创建一次性恢复码签发结果。
     *
     * @param summary 已写入数据库的集合状态
     * @param codes 只返回当前调用方且不得写入日志、审计或数据库的恢复码
     */
    public IssuedRecoveryCodes(
            @NotNull RecoveryCodeSummary summary, @NotNull List<String> codes) {
        this.summary = summary;
        this.codes = List.copyOf(codes);
    }

    /** @return 不包含秘密的恢复码集合状态 */
    public @NotNull RecoveryCodeSummary getSummary() {
        return summary;
    }

    /** @return 只允许当前响应展示一次的不可变恢复码集合 */
    public @NotNull List<String> getCodes() {
        return codes;
    }
}
