package com.sca4d.iam.identifier.service;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.identifier.AccountIdentifierService;
import com.sca4d.iam.identifier.AccountIdentifierType;
import com.sca4d.iam.identifier.repository.AccountIdentifierRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AccountIdentifierService 默认实现。 */
@Service
@RequiredArgsConstructor
public class DefaultAccountIdentifierService implements AccountIdentifierService {

    private final AccountService accountService;
    private final AccountIdentifierRepository repository;

    @Override
    public @NotNull IamAccountIdentifierEntity createUsername(
            @NotNull Long accountId, @NotNull String username) {
        IamAccountEntity account = accountService.require(accountId);
        String value = username.trim();
        String normalized = AccountIdentifierType.USERNAME.normalize(value);
        return repository.save(account, AccountIdentifierType.USERNAME, value, normalized, null);
    }

    @Override
    public @Nullable IamAccountIdentifierEntity findActive(
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId,
            @NotNull AccountIdentifierType type,
            @NotNull String value) {
        if ((scopeType == AccountScopeType.GLOBAL && scopeId != null)
                || (scopeType == AccountScopeType.TENANT && scopeId == null)) {
            throw new IllegalArgumentException("Account 作用域类型与主键不匹配");
        }
        String normalized = type.normalize(value.trim());
        return repository.findActive(scopeType, scopeId, type, normalized);
    }

    @Override
    public @NotNull List<IamAccountIdentifierEntity> findByAccount(@NotNull Long accountId) {
        accountService.require(accountId);
        return repository.findByAccount(accountId);
    }

    @Override
    public @NotNull Map<Long, List<IamAccountIdentifierEntity>> findByAccounts(
            @NotNull List<Long> accountIds) {
        List<Long> distinctIds = accountIds.stream().distinct().toList();
        Map<Long, List<IamAccountIdentifierEntity>> grouped = new LinkedHashMap<>();
        for (Long accountId : distinctIds) {
            grouped.put(accountId, new ArrayList<>());
        }
        // Repository 固定执行一条 IN 查询；结果按输入 Account 分组，不在循环中回源数据库。
        for (IamAccountIdentifierEntity identifier : repository.findByAccounts(distinctIds)) {
            List<IamAccountIdentifierEntity> identifiers = grouped.get(identifier.getAccountId());
            if (identifiers != null) {
                identifiers.add(identifier);
            }
        }
        Map<Long, List<IamAccountIdentifierEntity>> immutable = new LinkedHashMap<>();
        grouped.forEach((accountId, identifiers) -> immutable.put(accountId, List.copyOf(identifiers)));
        return Collections.unmodifiableMap(immutable);
    }
}
