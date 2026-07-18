package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** DEFAULT Principal 的默认资料持久化记录。 */
@Data
@Accessors(chain = true)
@Table("iam_default_profile")
public class IamDefaultProfileEntity {
    private Long principalId;
    private Long avatarFileId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
