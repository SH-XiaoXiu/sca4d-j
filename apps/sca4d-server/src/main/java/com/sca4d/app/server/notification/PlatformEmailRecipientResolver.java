package com.sca4d.app.server.notification;

import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.app.server.service.PlatformUserProfileService;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.iam.base.PlatformAccountDirectory;
import com.sca4d.iam.base.PlatformAccountEntry;
import com.sca4d.iam.identity.IdentityStatus;
import com.sca4d.notification.email.EmailRecipientResolver;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/** 从有效 Platform Account 的 App Profile 读取 Email 通知地址。 */
@Component
public class PlatformEmailRecipientResolver implements EmailRecipientResolver {

    private final @NotNull PlatformUserProfileService profiles;

    private final @NotNull PlatformAccountDirectory accounts;

    /**
     * @param accounts Platform Account 与唯一 Principal 目录
     * @param profiles Sca4d Server 用户资料服务
     */
    public PlatformEmailRecipientResolver(
            @NotNull PlatformAccountDirectory accounts,
            @NotNull PlatformUserProfileService profiles) {
        this.accounts = Objects.requireNonNull(accounts, "Platform Account 目录不能为空");
        this.profiles = Objects.requireNonNull(profiles, "Platform 用户资料服务不能为空");
    }

    @Override
    @Transactional(readOnly = true)
    public @NotNull Optional<String> resolve(@NotNull NotificationRecipient recipient) {
        NotificationRecipient checked = Objects.requireNonNull(recipient, "通知接收人不能为空");
        if (!NotificationRecipient.ACCOUNT_TYPE.equals(checked.type())) {
            return Optional.empty();
        }
        try {
            long accountId = Long.parseLong(checked.id());
            PlatformAccountEntry account = accounts.loadById(accountId);
            if (account.status() != IdentityStatus.ACTIVE) {
                return Optional.empty();
            }
            return profiles.find(account.principalId()).map(PlatformUserProfile::email);
        } catch (BusinessException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
