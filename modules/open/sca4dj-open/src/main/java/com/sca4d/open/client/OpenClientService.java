package com.sca4d.open.client;

import com.sca4d.auth.hmac.HmacAuthenticationMethod;
import com.sca4d.auth.hmac.IssuedHmacKey;
import com.sca4d.auth.sharedsecret.IssuedSharedSecret;
import com.sca4d.auth.sharedsecret.SharedSecretAuthenticationMethod;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Operation;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.iam.identity.IdentityService;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 创建、轮换、停用 Open Client，并限制其 Operation 为创建者当前权限的子集。
 */
public class OpenClientService {

    private static final @NotNull RealmKey REALM = new RealmKey("open");

    private final @NotNull IdentityService identities;

    private final @NotNull OpenClientRepository clients;

    private final @NotNull HmacAuthenticationMethod hmac;

    private final @NotNull OperationCatalog operations;

    private final @NotNull SharedSecretAuthenticationMethod sharedSecrets;

    /**
     * 创建 Open Client 应用服务。
     */
    public OpenClientService(
            @NotNull IdentityService identities,
            @NotNull OperationCatalog operations,
            @NotNull OpenClientRepository clients,
            @NotNull SharedSecretAuthenticationMethod sharedSecrets,
            @NotNull HmacAuthenticationMethod hmac) {
        this.identities = Objects.requireNonNull(identities, "身份服务不能为空");
        this.operations = Objects.requireNonNull(operations, "Operation 目录不能为空");
        this.clients = Objects.requireNonNull(clients, "Open Client Repository 不能为空");
        this.sharedSecrets = Objects.requireNonNull(sharedSecrets, "Shared Secret 方法不能为空");
        this.hmac = Objects.requireNonNull(hmac, "HMAC 方法不能为空");
    }

    /**
     * 根据当前 Session 创建 Application Client。
     *
     * @param creator             当前请求 Session；其中全部可选 Principal 会复制为 Client 关系
     * @param displayName         Client 管理名称
     * @param requestedOperations 要开放的 Operation
     * @param method              shared-secret 或 hmac
     * @return Client 元数据和一次性明文凭据
     */
    @Transactional
    @CheckReturnValue
    public @NotNull OpenClientCredentials create(
            @NotNull Session creator,
            @NotNull String displayName,
            @NotNull Collection<String> requestedOperations,
            @NotNull String method) {
        Session checkedCreator = Objects.requireNonNull(creator, "创建者 Session 不能为空");
        LinkedHashSet<TreeCode> allowed = validateOperations(checkedCreator, requestedOperations);
        String checkedMethod = requireMethod(method);
        Subject subject = identities.createSubject(SubjectType.APPLICATION);
        Account account = identities.createAccount(subject.subjectId(), REALM);
        checkedCreator.principals().forEach(principal -> identities.link(account.accountId(), principal.principalId()));
        String clientId = UUID.randomUUID().toString();
        OpenClient client = new OpenClient(
                clientId,
                account.accountId(),
                displayName,
                OpenClientStatus.ACTIVE);
        clients.create(client, allowed);
        if ("shared-secret".equals(checkedMethod)) {
            IssuedSharedSecret issued = sharedSecrets.issue(account);
            return new OpenClientCredentials(client, checkedMethod, issued.secret());
        }
        IssuedHmacKey issued = hmac.issue(account);
        return new OpenClientCredentials(client, checkedMethod, issued.key());
    }

    /**
     * 读取指定 Client。
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull OpenClient loadByClientId(@NotNull String clientId) {
        return clients.find(clientId).orElseThrow(() -> new IllegalArgumentException("Open Client 不存在"));
    }

    /**
     * 按认证 Account 读取对应 Client。
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull OpenClient findByAccountId(long accountId) {
        return clients.findByAccountId(accountId)
                .orElseThrow(() -> new IllegalArgumentException("认证 Account 不是 Open Client"));
    }

    /**
     * 读取仍然启用的 Client Account，供 MAC 认证提交给认证方法。
     */
    @Transactional(readOnly = true)
    public @NotNull Account account(long accountId) {
        return identities.activeAccountById(accountId);
    }

    /**
     * 判断 Client 是否有效且已经开放指定 Operation。
     */
    @Transactional(readOnly = true)
    public boolean allows(@NotNull String clientId, @NotNull TreeCode operation) {
        OpenClient client = loadByClientId(clientId);
        return client.active() && clients.hasOperation(client.clientId(), operation);
    }

    /**
     * 读取 Client 当前返回给 OAuth scope 的 Operation 列表。
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull List<@NotNull TreeCode> operations(@NotNull String clientId) {
        OpenClient client = loadByClientId(clientId);
        return clients.findOperations(client.clientId());
    }

    /**
     * 替换 Client 的 Operation 列表，仍要求当前 Session 拥有目标权限。
     */
    @Transactional
    public void replaceOperations(
            @NotNull Session editor,
            @NotNull String clientId,
            @NotNull Collection<String> requestedOperations) {
        LinkedHashSet<TreeCode> allowed = validateOperations(
                Objects.requireNonNull(editor, "编辑者 Session 不能为空"),
                requestedOperations);
        clients.replaceOperations(clientId, allowed);
    }

    /**
     * 修改 Client 状态。
     */
    @Transactional
    public void changeStatus(@NotNull String clientId, @NotNull OpenClientStatus status) {
        clients.updateStatus(clientId, status);
    }

    private LinkedHashSet<TreeCode> validateOperations(Session creator, Collection<String> requestedOperations) {
        LinkedHashSet<TreeCode> result = new LinkedHashSet<>();
        for (String code : Objects.requireNonNull(requestedOperations, "Operation 列表不能为空")) {
            Operation operation = operations.actionByCode(TextUtils.requireText(code, "Operation")).operation();
            if (creator.principals()
                    .stream()
                    .noneMatch(principal -> creator.grants(principal.principalId(), operation))) {
                throw new IllegalArgumentException("当前 Session 没有 Operation 权限: " + operation.code());
            }
            result.add(operation.code());
        }
        return result;
    }

    private static String requireMethod(String method) {
        String checked = TextUtils.requireText(method, "认证方法");
        if (!"shared-secret".equals(checked) && !"hmac".equals(checked)) {
            throw new IllegalArgumentException("Open 认证方法只能是 shared-secret 或 hmac");
        }
        return checked;
    }

}
