package com.sca4d.iam.profile.repository;

import lombok.Data;

/** DEFAULT Profile 与对应 Subject 的查询行。 */
@Data
public class DefaultProfileRow {
    private Long subjectId;
    private Long principalId;
    private Long avatarFileId;
    private Long version;
}
