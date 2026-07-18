package com.sca4d.authentication.view;

import com.sca4d.authentication.me.AvailablePrincipal;
import com.sca4d.authentication.me.MeSnapshot;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.file.view.FileSignatureRefView;
import com.sca4d.iam.principal.PrincipalStatus;
import com.sca4d.iam.profile.DefaultProfile;
import com.sca4d.iam.profile.DefaultProfileAvatar;
import com.sca4d.system.view.NavigationMenuView;
import com.sca4d.tenant.TenantMembership;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * 当前登录用户完整本地状态视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeView {

    private SubjectView subject;
    private AccountView account;
    private FileSignatureRefView avatar;
    private Long profileVersion;
    private PrincipalView principal;
    private MeSessionView session;
    private Set<String> roles;
    private Set<String> permissions;
    private List<AvailablePrincipalView> availablePrincipals;
    private List<TenantMembership> tenants;
    private List<NavigationMenuView> navigation;

    /**
     * 从认证授权快照创建视图。
     *
     * @param snapshot 当前认证运行时快照
     * @return 当前登录用户完整本地状态视图
     */
    public static @NotNull MeView from(@NotNull MeSnapshot snapshot) {
        DefaultProfile defaultProfile = snapshot.getDefaultProfile();
        FileSignatureRefView avatar = defaultProfile.getAvatarFileId() == null
                ? null
                : new FileSignatureRefView()
                        .setScene(DefaultProfileAvatar.FILE_SCENE)
                        .setResourceId(defaultProfile.getSubjectId().toString())
                        .setFileId(defaultProfile.getAvatarFileId());
        return new MeView(
                SubjectView.from(snapshot.getSubject()),
                AccountView.from(snapshot.getAccount()),
                avatar,
                defaultProfile.getVersion(),
                PrincipalView.from(snapshot.getPrincipal()),
                MeSessionView.from(snapshot.getSession()),
                Set.copyOf(snapshot.getPermission().getRoleCodes()),
                Set.copyOf(snapshot.getPermission().getPermissionCodes()),
                snapshot.getAvailablePrincipals().stream().map(AvailablePrincipalView::from).toList(),
                List.copyOf(snapshot.getTenants()),
                snapshot.getNavigation().stream().map(NavigationMenuView::from).toList());
    }

    /** 当前 Session 固定使用的 Account 视图。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountView {

        private Long accountId;
        private AccountScopeType scopeType;
        private Long scopeId;

        /**
         * 将运行时 Account 转换为不含登录标识和认证材料的响应结构。
         *
         * @param account 当前 Session 固定使用的 Account
         * @return Account 主键和作用域
         */
        static @NotNull AccountView from(@NotNull Account account) {
            return new AccountView(account.getAccountId(), account.getScopeType(), account.getScopeId());
        }
    }

    /**
     * Subject 视图。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectView {

        private Long subjectId;
        private String displayName;

        static @Nullable SubjectView from(@Nullable Subject subject) {
            if (subject == null) {
                return null;
            }
            return new SubjectView(subject.getSubjectId(), subject.getDisplayName());
        }
    }

    /**
     * Principal 视图。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrincipalView {

        private Long principalId;
        private PrincipalType principalType;
        private Long tenantId;
        private String displayName;

        static @Nullable PrincipalView from(@Nullable Principal principal) {
            if (principal == null) {
                return null;
            }
            return new PrincipalView(
                    principal.requirePrincipalId(),
                    principal.getPrincipalType(),
                    principal.getTenantId(),
                    principal.getDisplayName());
        }
    }

    /**
     * 客户端可以选择进入的 Principal 展示对象。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailablePrincipalView {

        private Long principalId;
        private PrincipalType principalType;
        private Long tenantId;
        private String displayName;
        private PrincipalStatus status;

        /**
         * 将运行时主体选择项转换为当前用户响应结构。
         *
         * @param principal 当前 Subject 的一个可选主体
         * @return 可被客户端用于后续请求选择的主体展示对象
         */
        static @NotNull AvailablePrincipalView from(@NotNull AvailablePrincipal principal) {
            return new AvailablePrincipalView(
                    principal.getPrincipalId(),
                    principal.getPrincipalType(),
                    principal.getTenantId(),
                    principal.getDisplayName(),
                    principal.getStatus());
        }
    }
}
