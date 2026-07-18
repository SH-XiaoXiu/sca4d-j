package com.sca4d.iam.recovery.repository;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.IamRecoveryCodeEntity;
import com.sca4d.entity.IamRecoveryCodeSetEntity;
import com.sca4d.iam.client.IamClientStateTargets;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.recovery.RecoveryCodeCandidate;
import com.sca4d.iam.recovery.RecoveryCodeSummary;
import com.sca4d.mapper.RecoveryCodeMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 恢复码集合替换、旧摘要删除和剩余数量读取仓储。 */
@Repository
@RequiredArgsConstructor
public class RecoveryCodeRepository {

    private final RecoveryCodeMapper mapper;
    private final IdGenerator idGenerator;

    /**
     * 使用一个新集合原子替换 Account 当前恢复码，并删除旧集合的全部摘要。
     *
     * @param accountId 所属 Account 主键
     * @param codeHashes 新集合的 Argon2id 编码摘要；不得包含恢复码明文
     * @return 新集合的非敏感状态
     */
    @Transactional
    public @NotNull RecoveryCodeSummary replace(
            @NotNull Long accountId, @NotNull List<String> codeHashes) {
        if (codeHashes.isEmpty() || codeHashes.stream().anyMatch(hash -> hash == null || hash.isBlank())) {
            throw new IllegalArgumentException("恢复码摘要集合不能为空");
        }
        OffsetDateTime issuedAt = TimeSupport.now();
        IamRecoveryCodeSetEntity current = mapper.selectCurrentSetForUpdate(accountId);
        if (current != null) {
            if (mapper.replaceSet(current.getId(), current.getVersion(), issuedAt) != 1) {
                throw new ConcurrencyConflictException(
                        IamErrorCode.ACCOUNT_CHANGED, IamClientStateTargets.ACCOUNTS);
            }
            // 集合替换后立即删除旧摘要；历史集合只保留签发和替换时间，不保留已经失效的秘密派生值。
            mapper.deleteCodes(current.getId());
        }
        Long setId = idGenerator.nextId();
        mapper.insertSet(new IamRecoveryCodeSetEntity()
                .setId(setId)
                .setAccountId(accountId)
                .setIssuedAt(issuedAt)
                .setVersion(0L));
        List<IamRecoveryCodeEntity> codes = codeHashes.stream()
                .map(hash -> new IamRecoveryCodeEntity()
                        .setId(idGenerator.nextId())
                        .setSetId(setId)
                        .setCodeHash(hash)
                        .setCreatedAt(issuedAt))
                .toList();
        mapper.insertCodes(codes);
        return new RecoveryCodeSummary(setId, accountId, issuedAt, codes.size(), 0L);
    }

    /**
     * 撤销当前集合并删除其中全部摘要；没有当前集合时保持幂等。
     *
     * @param accountId 已关闭第二因子的 Account
     */
    @Transactional
    public void revoke(@NotNull Long accountId) {
        IamRecoveryCodeSetEntity current = mapper.selectCurrentSetForUpdate(accountId);
        if (current == null) {
            return;
        }
        OffsetDateTime replacedAt = TimeSupport.now();
        if (mapper.replaceSet(current.getId(), current.getVersion(), replacedAt) != 1) {
            throw new ConcurrencyConflictException(
                    IamErrorCode.ACCOUNT_CHANGED, IamClientStateTargets.ACCOUNTS);
        }
        mapper.deleteCodes(current.getId());
    }

    /**
     * 批量读取当前恢复码集合；空输入固定为零条 SQL。
     *
     * @param accountIds Account 主键
     * @return 按 Account 主键索引且不包含摘要的集合状态
     */
    public @NotNull Map<Long, RecoveryCodeSummary> findCurrent(
            @NotNull Collection<Long> accountIds) {
        List<Long> distinctAccountIds = accountIds.stream().distinct().toList();
        if (distinctAccountIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, RecoveryCodeSummary> summaries = new LinkedHashMap<>();
        for (RecoveryCodeSummaryRow row : mapper.selectCurrentSummaries(distinctAccountIds)) {
            summaries.put(row.getAccountId(), new RecoveryCodeSummary(
                    row.getSetId(),
                    row.getAccountId(),
                    row.getIssuedAt(),
                    row.getRemainingCount(),
                    row.getVersion()));
        }
        return Map.copyOf(summaries);
    }

    /**
     * 读取 Account 当前集合中的未使用恢复码摘要。
     *
     * @param accountId 已由认证方式解析的 Account
     * @return 按恢复码主键排序的候选项；没有当前集合时为空集合
     */
    public @NotNull List<RecoveryCodeCandidate> findUnused(@NotNull Long accountId) {
        return mapper.selectUnusedCodes(accountId).stream()
                .map(row -> new RecoveryCodeCandidate(
                        row.getCodeId(),
                        row.getSetId(),
                        row.getAccountId(),
                        row.getCodeHash(),
                        row.getSetVersion()))
                .toList();
    }

    /**
     * 原子标记一条仍属于当前集合的恢复码已使用。
     *
     * @param candidate 已通过 Argon2id 校验的候选项
     * @return 当前请求取得单次使用权时为 true
     */
    public boolean use(@NotNull RecoveryCodeCandidate candidate) {
        return mapper.useCode(
                candidate.getAccountId(),
                candidate.getSetId(),
                candidate.getCodeId(),
                candidate.getSetVersion(),
                TimeSupport.now()) == 1;
    }

    /**
     * 检查恢复码 Proof 引用的记录仍属于 Account 当前集合。
     *
     * @param accountId 恢复码所属 Account
     * @param codeId 已使用的恢复码记录主键
     * @param setVersion Proof 保存的集合版本
     * @return 集合未替换且恢复码仍保持已使用状态时为 true
     */
    public boolean isCurrentUsed(
            @NotNull Long accountId, @NotNull Long codeId, @NotNull Long setVersion) {
        return mapper.countCurrentUsedCode(accountId, codeId, setVersion) == 1;
    }
}
