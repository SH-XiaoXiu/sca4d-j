package com.sca4d.core.api.authentication;

import com.sca4d.core.api.name.code.TreeCode;

import java.util.Objects;

/**
 * 调用方交给认证引擎的类型化认证方法 Proof。
 *
 * @param <P> Proof 类型
 */
@SuppressWarnings("ClassCanBeRecord")
public class AuthenticationSubmission<P> {

    private final Class<P> proofType;

    private final P proof;

    private final TreeCode methodCode;

    /**
     * 创建一次只能交给目标认证方法使用的 Proof 提交。
     *
     * @param methodCode 目标认证方法
     * @param proofType  Proof 精确类型
     * @param proof      只用于本次调用的 Proof
     * @throws NullPointerException     任一参数为空时抛出
     * @throws IllegalArgumentException Proof 的实际类型与声明类型不完全相同时抛出
     */
    public AuthenticationSubmission(TreeCode methodCode, Class<P> proofType, P proof) {
        this.methodCode = Objects.requireNonNull(methodCode, "认证方法编码不能为空");
        this.proofType = Objects.requireNonNull(proofType, "Proof 类型不能为空");
        this.proof = Objects.requireNonNull(proof, "Proof 不能为空");
        if (proof.getClass() != proofType) {
            throw new IllegalArgumentException("Proof 必须与声明类型精确一致");
        }
    }

    /**
     * 认证引擎应选择的认证方法。
     *
     * @return 目标认证方法编码
     */
    public TreeCode methodCode() {
        return methodCode;
    }

    /**
     * 认证引擎调用认证方法前必须核对的 Proof 类型。
     *
     * @return Proof 精确类型
     */
    public Class<P> proofType() {
        return proofType;
    }

    /**
     * 本次认证方法调用使用的证明材料。
     *
     * @return Proof；调用方和认证引擎都不得持久化或记录此值
     */
    public P proof() {
        return proof;
    }
}
