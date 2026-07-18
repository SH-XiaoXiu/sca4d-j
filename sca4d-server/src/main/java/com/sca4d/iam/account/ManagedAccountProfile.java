package com.sca4d.iam.account;

import com.sca4d.iam.profile.DefaultProfile;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 平台账号详情中可管理的 Subject 通用资料。 */
@Data
@AllArgsConstructor
public class ManagedAccountProfile {
    private Long accountId;
    private Long subjectId;
    private String displayName;
    private String phone;
    private String email;
    private DefaultProfile profile;
    private Long subjectVersion;
}
