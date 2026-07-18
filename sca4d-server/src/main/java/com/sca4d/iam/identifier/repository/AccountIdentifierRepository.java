package com.sca4d.iam.identifier.repository;

import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.dataaccess.PostgresConstraintViolation;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.identifier.AccountIdentifierStatus;
import com.sca4d.iam.identifier.AccountIdentifierType;
import com.sca4d.mapper.IamAccountIdentifierMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/** AccountIdentifier 的作用域唯一写入和登录解析仓储。 */
@Repository
@RequiredArgsConstructor
public class AccountIdentifierRepository {

    private final IamAccountIdentifierMapper mapper;
    private final IdGenerator idGenerator;

    /**
     * 为 Account 写入一个登录标识，并复制 Account 的不可变作用域字段。
     *
     * @param account 标识所属 Account
     * @param type USERNAME 或 EMAIL
     * @param value 页面展示使用的原值
     * @param normalizedValue 登录解析和唯一索引使用的规范值
     * @param contactId EMAIL 对应的已验证 Contact；USERNAME 为 {@code null}
     * @return 已写入数据库的 Identifier
     */
    public @NotNull IamAccountIdentifierEntity save(
            @NotNull IamAccountEntity account,
            @NotNull AccountIdentifierType type,
            @NotNull String value,
            @NotNull String normalizedValue,
            @Nullable Long contactId) {
        OffsetDateTime now = TimeSupport.now();
        IamAccountIdentifierEntity stored = new IamAccountIdentifierEntity()
                .setId(idGenerator.nextId())
                .setAccountId(account.getId())
                .setScopeType(account.getScopeType())
                .setScopeId(account.getScopeId())
                .setIdentifierType(type)
                .setValue(value)
                .setNormalizedValue(normalizedValue)
                .setContactId(contactId)
                .setStatus(AccountIdentifierStatus.ACTIVE)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setVersion(0L)
                .setDeleted(0);
        try {
            mapper.insert(stored);
        } catch (DataIntegrityViolationException exception) {
            if (PostgresConstraintViolation.matches(exception, "23505", "uk_iam_account_identifier_account_type")
                    || PostgresConstraintViolation.matches(exception, "23505", "uk_iam_account_identifier_global")
                    || PostgresConstraintViolation.matches(exception, "23505", "uk_iam_account_identifier_tenant")) {
                throw new BizException(IamErrorCode.IDENTIFIER_ALREADY_USED);
            }
            throw exception;
        }
        return new IamAccountIdentifierEntity().setId(stored.getId()).setAccountId(stored.getAccountId()).setScopeType(stored.getScopeType())
                .setScopeId(stored.getScopeId()).setIdentifierType(stored.getIdentifierType()).setValue(stored.getValue()).setContactId(stored.getContactId())
                .setStatus(stored.getStatus()).setVersion(stored.getVersion());
    }

    /**
     * 按身份作用域和规范值解析一个可用登录标识。
     *
     * @param scopeType GLOBAL 或 TENANT
     * @param scopeId TENANT 作用域主键；GLOBAL 为 {@code null}
     * @param type USERNAME 或 EMAIL
     * @param normalizedValue 已由 Service 规范化的登录输入
     * @return 唯一 ACTIVE Identifier；不存在时为 {@code null}
     */
    public @Nullable IamAccountIdentifierEntity findActive(
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId,
            @NotNull AccountIdentifierType type,
            @NotNull String normalizedValue) {
        QueryWrapper query = QueryWrapper.create()
                .from(IamAccountIdentifierEntity.class)
                .where(IamAccountIdentifierEntity::getScopeType)
                .eq(scopeType)
                .and(IamAccountIdentifierEntity::getIdentifierType)
                .eq(type)
                .and(IamAccountIdentifierEntity::getNormalizedValue)
                .eq(normalizedValue)
                .and(IamAccountIdentifierEntity::getStatus)
                .eq(AccountIdentifierStatus.ACTIVE)
                .and(IamAccountIdentifierEntity::getDeleted)
                .eq(0);
        if (scopeType == AccountScopeType.GLOBAL) {
            query.and(IamAccountIdentifierEntity::getScopeId).isNull();
        } else {
            query.and(IamAccountIdentifierEntity::getScopeId).eq(scopeId);
        }
        IamAccountIdentifierEntity stored = mapper.selectOneByQuery(query);
        return stored == null
                ? null
                : new IamAccountIdentifierEntity().setId(stored.getId()).setAccountId(stored.getAccountId()).setScopeType(stored.getScopeType())
                        .setScopeId(stored.getScopeId()).setIdentifierType(stored.getIdentifierType()).setValue(stored.getValue())
                        .setContactId(stored.getContactId()).setStatus(stored.getStatus()).setVersion(stored.getVersion());
    }

    /**
     * 读取 Account 当前未删除的登录标识。
     *
     * @param accountId Account 主键
     * @return 按类型和主键排序的 Identifier
     */
    public @NotNull List<IamAccountIdentifierEntity> findByAccount(@NotNull Long accountId) {
        return findByAccounts(List.of(accountId));
    }

    /**
     * 使用单条查询读取多个 Account 当前未删除的登录标识。
     *
     * @param accountIds 已去重的 Account 主键
     * @return 按 Account、类型和主键排序的 Identifier
     */
    public @NotNull List<IamAccountIdentifierEntity> findByAccounts(@NotNull List<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .from(IamAccountIdentifierEntity.class)
                .where(IamAccountIdentifierEntity::getAccountId)
                .in(accountIds)
                .and(IamAccountIdentifierEntity::getDeleted)
                .eq(0)
                .orderBy(IamAccountIdentifierEntity::getAccountId)
                .asc()
                .orderBy(IamAccountIdentifierEntity::getIdentifierType)
                .asc()
                .orderBy(IamAccountIdentifierEntity::getId)
                .asc();
        return mapper.selectListByQuery(query).stream()
                .map(stored -> new IamAccountIdentifierEntity().setId(stored.getId()).setAccountId(stored.getAccountId()).setScopeType(stored.getScopeType())
                        .setScopeId(stored.getScopeId()).setIdentifierType(stored.getIdentifierType()).setValue(stored.getValue())
                        .setContactId(stored.getContactId()).setStatus(stored.getStatus()).setVersion(stored.getVersion()))
                .toList();
    }
}
