package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.iam.contact.ContactStatus;
import com.sca4d.iam.contact.ContactType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_contact} 保存的 Subject 联系资料事实。 */
@Data
@Accessors(chain = true)
@Table("iam_contact")
public class IamContactEntity {
    private Long id;
    private Long subjectId;
    private ContactType contactType;
    private String value;
    private String normalizedValue;
    private Boolean primaryContact;
    private OffsetDateTime verifiedAt;
    private Long verifiedByProviderId;
    private ContactStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
