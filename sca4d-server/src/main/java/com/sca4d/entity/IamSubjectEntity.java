package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.iam.subject.SubjectStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_subject} 保存的登录身份事实。 */
@Data
@Accessors(chain = true)
@Table("iam_subject")
public class IamSubjectEntity {
    private Long id;
    private String displayName;
    private SubjectStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
