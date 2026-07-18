package com.sca4d.iam.account.service;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountSecondFactorAvailability;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.account.ManagedAccount;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.repository.IamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 创建和读取 GLOBAL/TENANT Account，并在启用二次验证前检查当前可用认证方式。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultAccountService implements AccountService {

    private final IamRepository iamRepository;
    private final AccountSecondFactorAvailability secondFactors;

    @Override
    public @NotNull IamAccountEntity create(
            @NotNull Long subjectId,
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId) {
        if (iamRepository.findSubject(subjectId) == null) {
            throw new BizException(IamErrorCode.SUBJECT_NOT_FOUND);
        }
        return iamRepository.createAccount(subjectId, scopeType, scopeId);
    }

    @Override
    public @NotNull IamAccountEntity require(@NotNull Long accountId) {
        IamAccountEntity account = iamRepository.findAccount(accountId);
        if (account == null) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    @Override
    public @NotNull List<IamAccountEntity> findBySubject(@NotNull Long subjectId) {
        return iamRepository.findAccountsBySubjectIds(List.of(subjectId));
    }

    @Override
    public @NotNull Map<Long, List<IamAccountEntity>> findBySubjects(@NotNull List<Long> subjectIds) {
        Map<Long, List<IamAccountEntity>> accounts = new LinkedHashMap<>();
        for (Long subjectId : subjectIds) {
            accounts.putIfAbsent(subjectId, new ArrayList<>());
        }
        for (IamAccountEntity account : iamRepository.findAccountsBySubjectIds(subjectIds)) {
            List<IamAccountEntity> subjectAccounts = accounts.get(account.getSubjectId());
            if (subjectAccounts != null) {
                subjectAccounts.add(account);
            }
        }
        Map<Long, List<IamAccountEntity>> immutable = new LinkedHashMap<>();
        accounts.forEach((subjectId, subjectAccounts) -> immutable.put(subjectId, List.copyOf(subjectAccounts)));
        return Collections.unmodifiableMap(immutable);
    }

    @Override
    public @NotNull PageData<ManagedAccount> page(
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId,
            @Nullable String search,
            @Nullable AccountStatus status,
            long page,
            long size) {
        return iamRepository.pageAccounts(scopeType, scopeId, search, status, page, size);
    }

    @Override
    public @NotNull IamAccountEntity changeSecondFactorRequirement(
            @NotNull Long accountId, boolean required, long version) {
        IamAccountEntity account = require(accountId);
        // 启用前由认证模块检查当前注册方式和所需资料，Account Service 不识别具体认证协议。
        if (required && !secondFactors.available(account)) {
            throw new BizException(IamErrorCode.ACCOUNT_SECOND_FACTOR_REQUIRED);
        }
        iamRepository.changeSecondFactorRequirement(accountId, required, version);
        IamAccountEntity updated = require(accountId);
        log.info(
                "登录账号二次验证要求更新完成 | accountId={} subjectId={} required={} version={}",
                accountId,
                updated.getSubjectId(),
                required,
                version + 1);
        return updated;
    }
}
