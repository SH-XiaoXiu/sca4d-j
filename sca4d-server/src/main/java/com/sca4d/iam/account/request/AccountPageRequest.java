package com.sca4d.iam.account.request;

import com.sca4d.common.validation.SafeText;
import com.sca4d.common.web.PageQuery;
import com.sca4d.iam.account.AccountStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Account 分页筛选条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AccountPageRequest extends PageQuery {
    @SafeText(max = 128)
    private String search;
    private AccountStatus status;
}
