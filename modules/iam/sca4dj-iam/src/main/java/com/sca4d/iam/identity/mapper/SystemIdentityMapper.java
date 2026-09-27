package com.sca4d.iam.identity.mapper;

import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.iam.identity.IdentityStatus;
import com.sca4d.iam.identity.PrincipalType;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/** 批量读取固定身份绑定，并原子插入本次新建 Principal 与对应绑定。 */
public interface SystemIdentityMapper {

    /** 返回指定编码的现有绑定；编码集合非空，不过滤 Principal 的状态。 */
    @CheckReturnValue
    @NotNull
    List<SystemPrincipal> selectBindings(@Param("codes") @NotNull Collection<TreeCode> codes);

    /**
     * 只为本条 SQL 实际新建的 Principal 插入绑定，不接管已占用 ID。
     *
     * @param identities 非空候选身份；编码和 ID 已由调用方去重
     * @param subjectId 所属 Subject 标识
     * @param principalType 写入 Principal 的稳定类型
     * @param status 新 Principal 的初始状态
     * @return 本次新增的绑定数量，冲突项不会覆盖
     */
    @CheckReturnValue
    int insertBindings(
            @Param("identities") @NotNull Collection<SystemPrincipal> identities,
            @Param("subjectId") long subjectId,
            @Param("principalType") @NotNull PrincipalType principalType,
            @Param("status") @NotNull IdentityStatus status);
}
