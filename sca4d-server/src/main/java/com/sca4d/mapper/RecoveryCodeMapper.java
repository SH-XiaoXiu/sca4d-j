package com.sca4d.mapper;

import com.sca4d.entity.IamRecoveryCodeEntity;
import com.sca4d.entity.IamRecoveryCodeSetEntity;
import com.sca4d.iam.recovery.repository.RecoveryCodeCandidateRow;
import com.sca4d.iam.recovery.repository.RecoveryCodeSummaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/** 恢复码集合替换、摘要删除和剩余数量查询 Mapper。 */
@Mapper
public interface RecoveryCodeMapper {

    /**
     * 锁定 Account 当前恢复码集合。
     *
     * @param accountId 已确认存在的 Account 主键
     * @return 当前集合；从未签发或已经撤销时为 {@code null}
     */
    @Nullable
    IamRecoveryCodeSetEntity selectCurrentSetForUpdate(@Param("accountId") @NotNull Long accountId);

    /**
     * 使用版本条件把当前集合标记为已替换。
     *
     * @param setId 当前恢复码集合主键
     * @param version 锁定行时读取的集合版本
     * @param replacedAt 集合失效时间
     * @return 主键、版本和当前状态全部匹配时为 1，否则为 0
     */
    int replaceSet(
            @Param("setId") @NotNull Long setId,
            @Param("version") @NotNull Long version,
            @Param("replacedAt") @NotNull OffsetDateTime replacedAt);

    /**
     * 删除一个失效集合的全部恢复码摘要。
     *
     * @param setId 已标记替换或撤销的集合主键
     * @return 删除的摘要数量
     */
    int deleteCodes(@Param("setId") @NotNull Long setId);

    /**
     * 写入一个新的当前恢复码集合。
     *
     * @param set 已分配主键和签发时间的集合
     */
    void insertSet(@Param("set") @NotNull IamRecoveryCodeSetEntity set);

    /**
     * 使用一条 INSERT 写入同一集合的全部恢复码摘要。
     *
     * @param codes 已分配主键且集合相同的非空摘要集合
     */
    void insertCodes(@Param("codes") @NotNull List<IamRecoveryCodeEntity> codes);

    /**
     * 批量统计 Account 当前恢复码集合及未使用数量。
     *
     * @param accountIds 去重且非空的 Account 主键
     * @return 只包含当前集合存在的 Account，按 Account 主键排序
     */
    @NotNull
    List<RecoveryCodeSummaryRow> selectCurrentSummaries(
            @Param("accountIds") @NotNull Collection<Long> accountIds);

    /**
     * 读取 Account 当前集合中全部未使用恢复码摘要。
     *
     * @param accountId 已由作用域内 Identifier 解析的 Account
     * @return 最多十条候选摘要；没有当前集合时为空集合
     */
    @NotNull
    List<RecoveryCodeCandidateRow> selectUnusedCodes(
            @Param("accountId") @NotNull Long accountId);

    /**
     * 仅在恢复码仍属于指定当前集合且尚未使用时写入使用时间。
     *
     * @param accountId 恢复码所属 Account
     * @param setId 当前恢复码集合主键
     * @param codeId 通过 Argon2id 校验的恢复码记录主键
     * @param setVersion 校验候选项时读取的集合版本
     * @param usedAt 成功创建 Proof 的时间
     * @return 全部条件匹配时为 1，否则为 0
     */
    int useCode(
            @Param("accountId") @NotNull Long accountId,
            @Param("setId") @NotNull Long setId,
            @Param("codeId") @NotNull Long codeId,
            @Param("setVersion") @NotNull Long setVersion,
            @Param("usedAt") @NotNull OffsetDateTime usedAt);

    /**
     * 检查已使用恢复码是否仍属于 Account 当前集合。
     *
     * @param accountId 恢复码所属 Account
     * @param codeId 已使用的恢复码记录主键
     * @param setVersion Proof 保存的集合版本
     * @return 当前集合、版本和已使用状态全部匹配时为 1，否则为 0
     */
    int countCurrentUsedCode(
            @Param("accountId") @NotNull Long accountId,
            @Param("codeId") @NotNull Long codeId,
            @Param("setVersion") @NotNull Long setVersion);
}
