package com.sca4d.im;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.BizIdMode;
import org.jetbrains.annotations.NotNull;

/**
 * 创建会话时使用的成员 Principal 引用。
 *
 * <p>该类型只携带定位 IAM Principal 所需的 principalId。成员 Subject、租户、展示名称和状态始终从 IAM 表读取。
 */
public class ImMemberReference {

    @NotNull
    private PrincipalType principalType;

    @NotNull
    @BizId(mode = BizIdMode.NUM)
    private Long principalId;

    /**
     * 获取客户端声明的 Principal 类型。
     *
     * @return 用于复核 IAM Principal 类型的声明值
     */
    public @NotNull PrincipalType getPrincipalType() {
        return principalType;
    }

    /**
     * 设置客户端声明的 Principal 类型。
     *
     * @param principalType 用于复核 IAM Principal 类型的声明值
     */
    public void setPrincipalType(@NotNull PrincipalType principalType) {
        this.principalType = principalType;
    }

    /**
     * 获取目标 Principal 主键。
     *
     * @return 需要从 IAM 读取的 Principal 主键
     */
    public @NotNull Long getPrincipalId() {
        return principalId;
    }

    /**
     * 设置目标 Principal 主键。
     *
     * @param principalId 需要从 IAM 读取的 Principal 主键
     */
    public void setPrincipalId(@NotNull Long principalId) {
        this.principalId = principalId;
    }
}
