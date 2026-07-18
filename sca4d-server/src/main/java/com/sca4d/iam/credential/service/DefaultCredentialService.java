package com.sca4d.iam.credential.service;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialScheme;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.credential.CredentialVerification;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.iam.subject.SubjectLock;
import com.sca4d.iam.subject.SubjectStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 按认证材料 Java 类型选择 CredentialScheme，并在 Subject 集群锁内绑定、替换和管理 Credential。 */
@Service
public class DefaultCredentialService implements CredentialService {

    private final Map<Class<?>, CredentialScheme<?, ?>> schemes;
    private final Map<Class<?>, CredentialScheme<?, ?>> proofSchemes;
    private final Map<CredentialType, CredentialScheme<?, ?>> schemesByType;
    private final SubjectLock subjectLock;
    private final AccountService accountService;
    private final IamRepository iamRepository;

    /**
     * 建立认证材料 Java 类型到唯一 Scheme 的固定分派表。
     *
     * @param schemes 当前应用提供的全部 Credential Scheme
     * @param subjectLock 登录和 Credential 修改共用的 Subject 锁
     * @param iamRepository 重新查询 Credential 状态和版本的仓储
     */
    public DefaultCredentialService(
            @NotNull List<CredentialScheme<?, ?>> schemes,
            @NotNull SubjectLock subjectLock,
            @NotNull AccountService accountService,
            @NotNull IamRepository iamRepository) {
        // 每种认证材料 Java 类型只能有一个 Scheme，启动阶段拒绝重复注册以避免认证路径依赖 Bean 遍历顺序。
        Map<Class<?>, CredentialScheme<?, ?>> registered = new LinkedHashMap<>();
        Map<Class<?>, CredentialScheme<?, ?>> registeredProofs = new LinkedHashMap<>();
        Map<CredentialType, CredentialScheme<?, ?>> registeredByType = new LinkedHashMap<>();
        for (CredentialScheme<?, ?> scheme : schemes) {
            CredentialScheme<?, ?> previous = registered.putIfAbsent(scheme.credentialClass(), scheme);
            if (previous != null) {
                throw new IllegalStateException("重复 CredentialScheme: " + scheme.credentialClass().getName());
            }
            if (registeredProofs.putIfAbsent(scheme.proofClass(), scheme) != null) {
                throw new IllegalStateException("重复 Credential Proof Scheme: " + scheme.proofClass().getName());
            }
            if (registeredByType.putIfAbsent(scheme.credentialType(), scheme) != null) {
                throw new IllegalStateException("重复 CredentialType Scheme: " + scheme.credentialType());
            }
        }
        this.schemes = Map.copyOf(registered);
        this.proofSchemes = Map.copyOf(registeredProofs);
        this.schemesByType = Map.copyOf(registeredByType);
        this.subjectLock = subjectLock;
        this.accountService = accountService;
        this.iamRepository = iamRepository;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <C> @NotNull Long bind(@NotNull Long accountId, @NotNull C credential) {
        // Scheme 已依据 Java 类校验完成；此处的受控原始类型调用避免将重复转换步骤散落到各认证材料实现。
        CredentialScheme scheme = scheme(credential);
        IamAccountEntity account = accountService.require(accountId);
        return (Long) subjectLock.execute(account.getSubjectId(), () -> {
            // 取得 Subject 锁后重新读取 Account，锁前快照已经删除时不能继续写入孤立 Credential。
            accountService.require(accountId);
            return scheme.bind(accountId, credential);
        });
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <C> void replace(
            @NotNull Long accountId,
            @NotNull Long credentialId,
            @NotNull Long version,
            @NotNull C credential) {
        // 替换与绑定使用同一套 Java 类型校验，版本和唯一性规则由对应的 Scheme 检查。
        CredentialScheme scheme = scheme(credential);
        IamAccountEntity account = accountService.require(accountId);
        subjectLock.execute(account.getSubjectId(), () -> {
            // 锁内重新读取 Account，避免锁前读取与 Credential 更新之间发生删除。
            accountService.require(accountId);
            scheme.replace(accountId, credentialId, version, credential);
            return null;
        });
    }

    @Override
    public @NotNull List<IamCredentialEntity> findBySubject(@NotNull Long subjectId) {
        return findBySubjects(List.of(subjectId)).get(subjectId);
    }

    @Override
    public @NotNull List<IamCredentialEntity> findByAccount(@NotNull Long accountId) {
        accountService.require(accountId);
        return iamRepository.findCredentialsByAccount(accountId);
    }

    @Override
    public @NotNull Map<Long, List<IamCredentialEntity>> findBySubjects(@NotNull List<Long> subjectIds) {
        List<Long> distinctSubjectIds = subjectIds.stream().distinct().toList();
        Set<CredentialType> supportedTypes = schemesByType.keySet();
        return iamRepository.findCredentials(distinctSubjectIds, supportedTypes);
    }

    @Override
    public void changeStatus(
            @NotNull Long accountId,
            @NotNull Long credentialId,
            @NotNull CredentialStatus status,
            @NotNull Long version) {
        IamAccountEntity account = accountService.require(accountId);
        subjectLock.execute(account.getSubjectId(), () -> {
            // 锁内重新读取 Account，避免对已经删除的账号修改 Credential 状态。
            accountService.require(accountId);
            requireManagedCredential(accountId, credentialId);
            iamRepository.updateCredentialStatus(credentialId, accountId, status, version);
            return null;
        });
    }

    @Override
    public void delete(
            @NotNull Long accountId, @NotNull Long credentialId, @NotNull Long version) {
        IamAccountEntity account = accountService.require(accountId);
        subjectLock.execute(account.getSubjectId(), () -> {
            // 锁内重新读取 Account，避免锁前读取与 Credential 删除之间发生账号删除。
            accountService.require(accountId);
            requireManagedCredential(accountId, credentialId);
            iamRepository.deleteCredential(credentialId, accountId, version);
            return null;
        });
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <P> @NotNull CredentialVerification verify(
            @NotNull Long accountId, @NotNull P proof) {
        CredentialScheme scheme = proofScheme(proof);
        return scheme.verify(accountId, proof);
    }

    @Override
    public @NotNull CredentialVerification requireCurrent(@NotNull CredentialVerification verification) {
        Subject subject = verification.getSubject();
        Account account = verification.getAccount();
        if (!iamRepository.isCredentialCurrent(
                verification.getCredentialId(), account.getAccountId(), verification.getCredentialVersion())) {
            throw new UnauthenticatedException();
        }
        IamAccountEntity storedAccount = iamRepository.findAccount(account.getAccountId());
        if (storedAccount == null
                || storedAccount.getStatus() != AccountStatus.ACTIVE
                || !subject.getSubjectId().equals(storedAccount.getSubjectId())) {
            throw new UnauthenticatedException();
        }
        var storedSubject = iamRepository.findSubject(subject.getSubjectId());
        if (storedSubject == null || storedSubject.getStatus() != SubjectStatus.ACTIVE) {
            throw new UnauthenticatedException();
        }
        return new CredentialVerification(
                new Subject(storedSubject.getId(), storedSubject.getDisplayName()),
                new Account(
                        storedAccount.getId(),
                        storedAccount.getSubjectId(),
                        storedAccount.getScopeType(),
                        storedAccount.getScopeId(),
                        Boolean.TRUE.equals(storedAccount.getSecondFactorRequired())),
                verification.getCredentialId(),
                verification.getCredentialVersion());
    }

    /**
     * 选择并校验当前认证材料唯一对应的 CredentialScheme。
     *
     * <p>按认证材料的运行时 Java 类型定位 Scheme，再验证其声明类型，防止错误对象绕过并进入
     * 持久化认证逻辑。
     *
     * @param credential 调用方传入的认证材料
     * @return 已确认能够处理该材料运行时类型的 Scheme
     * @throws BizException 缺少类型对应 Scheme 或材料类型不匹配时抛出
     */
    private @NotNull CredentialScheme<?, ?> scheme(@NotNull Object credential) {
        CredentialScheme<?, ?> scheme = schemes.get(credential.getClass());
        if (scheme == null) {
            throw new BizException(IamErrorCode.CREDENTIAL_TYPE_UNSUPPORTED);
        }
        if (!scheme.credentialClass().isInstance(credential)) {
            throw new BizException(IamErrorCode.CREDENTIAL_TYPE_MISMATCH);
        }
        return scheme;
    }

    /**
     * 按 Proof 的精确 Java 类型选择唯一 Scheme。
     *
     * @param proof 当前认证操作提交的 Proof
     * @return 注册该 Proof 类型的 Scheme
     * @throws BizException Proof 类型未注册时抛出
     */
    private @NotNull CredentialScheme<?, ?> proofScheme(@NotNull Object proof) {
        CredentialScheme<?, ?> scheme = proofSchemes.get(proof.getClass());
        if (scheme == null) {
            throw new BizException(IamErrorCode.CREDENTIAL_TYPE_UNSUPPORTED);
        }
        return scheme;
    }

    /**
     * 确认目标 Credential 属于指定 Account 且当前类型已有真实 Scheme。
     *
     * @param accountId 管理路由中的 Account 主键
     * @param credentialId 目标 Credential 主键
     * @return 当前安全凭证读取对象
     * @throws BizException 凭证不存在、归属不匹配或类型未注册时抛出
     */
    private @NotNull IamCredentialEntity requireManagedCredential(
            @NotNull Long accountId, @NotNull Long credentialId) {
        IamCredentialEntity credential = iamRepository.findCredentialsByAccount(accountId).stream()
                .filter(item -> credentialId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BizException(IamErrorCode.CREDENTIAL_NOT_FOUND));
        if (!schemesByType.containsKey(credential.getCredentialType())) {
            throw new BizException(IamErrorCode.CREDENTIAL_TYPE_UNSUPPORTED);
        }
        return credential;
    }

}
