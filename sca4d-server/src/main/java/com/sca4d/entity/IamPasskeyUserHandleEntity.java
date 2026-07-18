package com.sca4d.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/** {@code iam_passkey_user_handle} 保存 Account 的 WebAuthn 不透明用户标识。 */
@Data
@Accessors(chain = true)
@Table("iam_passkey_user_handle")
public class IamPasskeyUserHandleEntity {
    @Id
    private Long accountId;

    private byte[] userHandle;
    private OffsetDateTime createdAt;
}
