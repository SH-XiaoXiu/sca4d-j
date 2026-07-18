package com.sca4d.iam.contact.repository;

import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.IamContactEntity;
import com.sca4d.iam.contact.ContactStatus;
import com.sca4d.iam.contact.ContactType;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.mapper.IamContactMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/** Contact 当前值的读取和替换仓储。 */
@Repository
@RequiredArgsConstructor
public class ContactRepository {

    private final IamContactMapper mapper;
    private final IdGenerator idGenerator;

    /**
     * 读取 Subject 指定类型的当前 Contact。
     *
     * @param subjectId Contact 所属 Subject
     * @param type EMAIL 或 PHONE
     * @return 当前未删除 Contact；尚未设置时为 {@code null}
     */
    public @Nullable IamContactEntity findCurrent(
            @NotNull Long subjectId, @NotNull ContactType type) {
        QueryWrapper query = QueryWrapper.create()
                .from(IamContactEntity.class)
                .where(IamContactEntity::getSubjectId)
                .eq(subjectId)
                .and(IamContactEntity::getContactType)
                .eq(type)
                .and(IamContactEntity::getPrimaryContact)
                .eq(true)
                .and(IamContactEntity::getDeleted)
                .eq(0);
        IamContactEntity contact = mapper.selectOneByQuery(query);
        return contact == null
                ? null
                : new IamContactEntity().setId(contact.getId()).setSubjectId(contact.getSubjectId()).setContactType(contact.getContactType())
                        .setValue(contact.getValue()).setVerifiedAt(contact.getVerifiedAt()).setStatus(contact.getStatus()).setVersion(contact.getVersion());
    }

    /** @return 指定未删除 Contact；不存在时为 {@code null} */
    public @Nullable IamContactEntity find(@NotNull Long contactId) {
        QueryWrapper query = QueryWrapper.create()
                .from(IamContactEntity.class)
                .where(IamContactEntity::getId)
                .eq(contactId)
                .and(IamContactEntity::getDeleted)
                .eq(0);
        IamContactEntity contact = mapper.selectOneByQuery(query);
        return contact == null
                ? null
                : new IamContactEntity().setId(contact.getId()).setSubjectId(contact.getSubjectId()).setContactType(contact.getContactType())
                        .setValue(contact.getValue()).setVerifiedAt(contact.getVerifiedAt()).setStatus(contact.getStatus()).setVersion(contact.getVersion());
    }

    /**
     * 使用 Contact 版本原子记录验证时间。
     *
     * @return 验证完成后的 Contact
     */
    public @NotNull IamContactEntity verify(
            @NotNull Long contactId,
            @NotNull Long subjectId,
            @NotNull ContactType type,
            long expectedVersion) {
        OffsetDateTime now = TimeSupport.now();
        IamContactEntity update = new IamContactEntity()
                .setVerifiedAt(now)
                .setUpdatedAt(now)
                .setVersion(expectedVersion + 1);
        QueryWrapper condition = QueryWrapper.create()
                .where(IamContactEntity::getId)
                .eq(contactId)
                .and(IamContactEntity::getSubjectId)
                .eq(subjectId)
                .and(IamContactEntity::getContactType)
                .eq(type)
                .and(IamContactEntity::getPrimaryContact)
                .eq(true)
                .and(IamContactEntity::getStatus)
                .eq(ContactStatus.ACTIVE)
                .and(IamContactEntity::getVersion)
                .eq(expectedVersion)
                .and(IamContactEntity::getDeleted)
                .eq(0);
        if (mapper.updateByQuery(update, condition) != 1) {
            throw new ConcurrencyConflictException(IamErrorCode.CONTACT_CHANGED, null);
        }
        IamContactEntity verified = find(contactId);
        if (verified == null) {
            throw new ConcurrencyConflictException(IamErrorCode.CONTACT_CHANGED, null);
        }
        return verified;
    }

    /**
     * 删除原有当前值并按未验证状态写入新 Contact。
     *
     * @param subjectId Contact 所属 Subject
     * @param type EMAIL 或 PHONE
     * @param value 页面展示使用的原值；为 {@code null} 时只删除当前值
     * @param normalizedValue 查重和关联使用的规范值；删除时为 {@code null}
     */
    @Transactional
    public void replace(
            @NotNull Long subjectId,
            @NotNull ContactType type,
            @Nullable String value,
            @Nullable String normalizedValue) {
        mapper.deleteCurrent(subjectId, type);
        if (value == null) {
            return;
        }
        OffsetDateTime now = TimeSupport.now();
        mapper.insert(new IamContactEntity()
                .setId(idGenerator.nextId())
                .setSubjectId(subjectId)
                .setContactType(type)
                .setValue(value)
                .setNormalizedValue(normalizedValue)
                .setPrimaryContact(true)
                .setStatus(ContactStatus.ACTIVE)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setVersion(0L)
                .setDeleted(0));
    }
}
