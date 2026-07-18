package com.sca4d.iam.account;

import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.entity.IamSubjectEntity;
import lombok.Data;
import lombok.experimental.Accessors;

/** 创建 Account 时同时写入的 Subject、DEFAULT Principal 和 Account。 */
@Data
@Accessors(chain = true)
public class ProvisionedAccount {
    private IamSubjectEntity subject;
    private IamPrincipalEntity defaultPrincipal;
    private IamAccountEntity account;
}
