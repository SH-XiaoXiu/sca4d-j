package com.sca4d.iam.contact.service;

import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamContactEntity;
import com.sca4d.iam.contact.ContactService;
import com.sca4d.iam.contact.ContactStatus;
import com.sca4d.iam.contact.ContactType;
import com.sca4d.iam.contact.repository.ContactRepository;
import com.sca4d.iam.error.IamErrorCode;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** ContactService 默认实现。 */
@Service
@RequiredArgsConstructor
public class DefaultContactService implements ContactService {

    private final ContactRepository repository;

    @Override
    public void replace(
            @NotNull Long subjectId, @NotNull ContactType type, @Nullable String value) {
        String checkedValue = StringUtils.hasText(value) ? value.trim() : null;
        String normalizedValue = checkedValue == null ? null : type.normalize(checkedValue);
        IamContactEntity current = repository.findCurrent(subjectId, type);
        if (current == null && checkedValue == null) {
            return;
        }
        // 同一个规范值再次提交时保留原 Contact，因此已经完成的验证事实不会被资料保存操作清除。
        if (current != null && type.normalize(current.getValue()).equals(normalizedValue)) {
            return;
        }
        repository.replace(subjectId, type, checkedValue, normalizedValue);
    }

    @Override
    public @Nullable IamContactEntity current(
            @NotNull Long subjectId, @NotNull ContactType type) {
        IamContactEntity contact = repository.findCurrent(subjectId, type);
        return contact == null || contact.getStatus() != ContactStatus.ACTIVE ? null : contact;
    }

    @Override
    public @NotNull IamContactEntity verify(
            @NotNull Long subjectId,
            @NotNull Long contactId,
            @NotNull ContactType type,
            long expectedVersion) {
        IamContactEntity contact = current(subjectId, type);
        if (contact == null || !contactId.equals(contact.getId())) {
            throw new BizException(IamErrorCode.CONTACT_CHANGED);
        }
        if (contact.getVerifiedAt() != null) {
            long currentVersion = contact.getVersion();
            if (currentVersion == expectedVersion
                    || (expectedVersion < Long.MAX_VALUE && currentVersion == expectedVersion + 1)) {
                return contact;
            }
            throw new BizException(IamErrorCode.CONTACT_CHANGED);
        }
        if (expectedVersion != contact.getVersion()) {
            throw new BizException(IamErrorCode.CONTACT_CHANGED);
        }
        return repository.verify(contactId, subjectId, type, expectedVersion);
    }

    @Override
    public @NotNull IamContactEntity requireVerified(
            @NotNull Long subjectId,
            @NotNull Long contactId,
            @NotNull ContactType type,
            long version) {
        IamContactEntity contact = current(subjectId, type);
        if (contact == null
                || !contactId.equals(contact.getId())
                || version != contact.getVersion()
                || contact.getVerifiedAt() == null) {
            throw new BizException(IamErrorCode.CONTACT_CHANGED);
        }
        return contact;
    }
}
