package com.sca4d.iam.profile;

import lombok.Data;
import lombok.experimental.Accessors;

/** Subject 通过 DEFAULT Principal 使用的默认头像资料。 */
@Data
@Accessors(chain = true)
public class DefaultProfile {
    private Long subjectId;
    private Long principalId;
    private Long avatarFileId;
    private Long version;
}
