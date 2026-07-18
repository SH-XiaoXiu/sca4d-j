package com.sca4d.authentication.flowable;

import com.sca4d.authentication.AuthMethod;
import com.sca4d.authentication.AuthMethodCapability;
import com.sca4d.authentication.AuthMethodCatalog;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthMethodVerification;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.AuthenticationProofReviewer;
import com.sca4d.authentication.AuthenticationPurpose;
import com.sca4d.authentication.AuthenticationScope;
import com.sca4d.authentication.AuthenticationScopeService;
import com.sca4d.authentication.SessionService;
import com.sca4d.authentication.error.AuthenticationErrorCode;
import com.sca4d.authentication.recovery.ConsumedAccountRecovery;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Session;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.enums.BaseEnum;
import com.sca4d.common.exception.BizException;
import com.sca4d.config.properties.Sca4djAuthenticationProperties;
import com.sca4d.iam.subject.SubjectLock;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 使用 Flowable ProcessInstance 和 UserTask 创建、验证并单次完成认证。 */
@Service
@Slf4j
public class FlowableAuthenticationProcessService implements AuthenticationProcessService {
    private static final String PURPOSE = "purpose";
    private static final String SCOPE_TYPE = "scopeType";
    private static final String SCOPE_ID = "scopeId";
    private static final String BROWSER_HASH = "browserHash";
    private static final String DEVICE_ID = "deviceId";
    private static final String TERMINAL_NAME = "terminalName";
    private static final String SUBJECT_ID = "subjectId";
    private static final String ACCOUNT_ID = "accountId";
    private static final String PRIMARY_METHOD_TYPE = "primaryMethodType";
    private static final String PRIMARY_PROOF_ID = "primaryProofId";
    private static final String PRIMARY_PROOF_VERSION = "primaryProofVersion";
    private static final String SECOND_FACTOR_REQUIRED = "secondFactorRequired";
    private static final String SECOND_FACTOR_METHOD_TYPE = "secondFactorMethodType";
    private static final String SECOND_FACTOR_PROOF_ID = "secondFactorProofId";
    private static final String SECOND_FACTOR_PROOF_VERSION = "secondFactorProofVersion";
    private static final String CREATED_AT = "createdAt";
    private static final String EXPIRES_AT = "expiresAt";
    private static final String AUTHENTICATION_TTL = "authenticationTtl";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final AuthenticationScopeService authenticationScopes;
    private final AuthMethodCatalog authMethods;
    private final AuthenticationProofReviewer proofReviewer;
    private final SubjectLock subjectLock;
    private final SessionService sessionService;
    private final Sca4djAuthenticationProperties properties;
    private final TransactionTemplate completionTransaction;

    public FlowableAuthenticationProcessService(
            RuntimeService runtimeService,
            TaskService taskService,
            AuthenticationScopeService authenticationScopes,
            AuthMethodCatalog authMethods,
            AuthenticationProofReviewer proofReviewer,
            SubjectLock subjectLock,
            SessionService sessionService,
            Sca4djAuthenticationProperties properties,
            PlatformTransactionManager transactionManager) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.authenticationScopes = authenticationScopes;
        this.authMethods = authMethods;
        this.proofReviewer = proofReviewer;
        this.subjectLock = subjectLock;
        this.sessionService = sessionService;
        this.properties = properties;
        this.completionTransaction = new TransactionTemplate(transactionManager);
        this.completionTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public @NotNull AuthenticationProcess createAnonymous(
            @NotNull AuthenticationPurpose purpose,
            @Nullable AccountScopeType scopeType,
            @Nullable String tenantCode,
            @NotNull String browserHash,
            @Nullable String deviceId,
            @Nullable String terminalName) {
        if (purpose != AuthenticationPurpose.LOGIN
                && purpose != AuthenticationPurpose.REGISTRATION
                && purpose != AuthenticationPurpose.RECOVERY) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_PURPOSE_UNAVAILABLE);
        }
        AuthenticationScope scope = authenticationScopes.resolve(scopeType, tenantCode);
        return start(
                purpose,
                scope.getScopeType(),
                scope.getScopeId(),
                browserHash,
                deviceId,
                terminalName,
                null,
                null);
    }

    @Override
    public @NotNull AuthenticationProcess createAuthenticated(
            @NotNull AuthenticationPurpose purpose,
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull String browserHash,
            @Nullable String deviceId,
            @Nullable String terminalName) {
        if (purpose != AuthenticationPurpose.STEP_UP
                && purpose != AuthenticationPurpose.LINKING) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_PURPOSE_UNAVAILABLE);
        }
        if (!subject.getSubjectId().equals(account.getSubjectId())) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
        }
        return start(
                purpose,
                account.getScopeType(),
                account.getScopeId(),
                browserHash,
                deviceId,
                terminalName,
                subject.getSubjectId(),
                account.getAccountId());
    }

    @Override
    public @NotNull AuthenticationProcess claimPrimary(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull AuthMethodType methodType) {
        AuthenticationProcess process = requireCurrent(authenticationId, browserHash);
        if (!AuthenticationProcessDefinition.PRIMARY_PROOF.equals(process.getActivityId())) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_ACTIVITY_INVALID);
        }
        AuthMethod<?> method = authMethods.require(methodType);
        if (!method.capabilities().contains(AuthMethodCapability.from(process.getPurpose()))) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_PURPOSE_UNAVAILABLE);
        }
        Task task = currentTask(authenticationId);
        try {
            taskService.claim(task.getId(), methodType.value());
        } catch (FlowableTaskAlreadyClaimedException | FlowableOptimisticLockingException exception) {
            log.info(
                    "认证方式已被其他请求选定 | authenticationId={} activityId={} purpose={} methodType={}",
                    authenticationId,
                    process.getActivityId(),
                    process.getPurpose(),
                    methodType);
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
        }
        log.info(
                "认证方式已选定 | authenticationId={} activityId={} purpose={} methodType={}",
                authenticationId,
                process.getActivityId(),
                process.getPurpose(),
                methodType);
        return requireCurrent(authenticationId, browserHash);
    }

    @Override
    public <P> @NotNull AuthenticationProcess proof(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull AuthMethodType methodType,
            @NotNull Class<P> proofClass,
            @NotNull P proof) {
        AuthenticationProcess process = requireProof(authenticationId, browserHash, methodType);
        boolean primary = AuthenticationProcessDefinition.PRIMARY_PROOF.equals(process.getActivityId());
        AuthMethod<P> method = authMethods.require(methodType, proofClass);

        // 密码、邮箱验证码、TOTP 验证码、恢复码、OAuth/OIDC Token、LDAP 密码和 WebAuthn 响应只交给对应 AuthMethod。
        // Flowable 变量只保存认证方式、Subject ID、Account ID、凭据或外部身份 ID 及其版本号。
        AuthMethodVerification verification = method.verify(process, proof);
        if (verification.getMethodType() != methodType
                || !verification.getSubject().getSubjectId().equals(verification.getAccount().getSubjectId())
                || verification.getAccount().getScopeType() != process.getScopeType()
                || !Objects.equals(verification.getAccount().getScopeId(), process.getScopeId())
                || (process.getSubjectId() != null
                        && !process.getSubjectId().equals(verification.getSubject().getSubjectId()))
                || (process.getAccountId() != null
                        && !process.getAccountId().equals(verification.getAccount().getAccountId()))) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        if (primary) {
            variables.put(SUBJECT_ID, verification.getSubject().getSubjectId());
            variables.put(ACCOUNT_ID, verification.getAccount().getAccountId());
            variables.put(PRIMARY_METHOD_TYPE, methodType.value());
            variables.put(PRIMARY_PROOF_ID, verification.getProofId());
            variables.put(PRIMARY_PROOF_VERSION, verification.getProofVersion());
            variables.put(
                    SECOND_FACTOR_REQUIRED,
                    process.getPurpose() == AuthenticationPurpose.LOGIN
                            && verification.getAccount().isSecondFactorRequired());
        } else {
            variables.put(SECOND_FACTOR_METHOD_TYPE, methodType.value());
            variables.put(SECOND_FACTOR_PROOF_ID, verification.getProofId());
            variables.put(SECOND_FACTOR_PROOF_VERSION, verification.getProofVersion());
        }
        completeTask(currentTask(authenticationId), variables, process, methodType);
        AuthenticationProcess advanced = requireCurrent(authenticationId, browserHash);
        log.info(
                "认证方式验证通过 | authenticationId={} activityId={} purpose={} methodType={} subjectId={} accountId={}",
                authenticationId,
                advanced.getActivityId(),
                advanced.getPurpose(),
                methodType,
                advanced.getSubjectId(),
                advanced.getAccountId());
        return advanced.getPurpose() == AuthenticationPurpose.LINKING
                ? completeLinking(authenticationId, browserHash)
                : advanced;
    }

    @Override
    public @NotNull AuthenticationProcess requireProof(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull AuthMethodType methodType) {
        AuthenticationProcess process = requireCurrent(authenticationId, browserHash);
        boolean primary = AuthenticationProcessDefinition.PRIMARY_PROOF.equals(process.getActivityId());
        boolean secondFactor = AuthenticationProcessDefinition.SECOND_FACTOR_PROOF.equals(process.getActivityId());
        if (!primary && !secondFactor) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_ACTIVITY_INVALID);
        }
        AuthMethod<?> method = authMethods.require(methodType);
        AuthMethodCapability capability = primary
                ? AuthMethodCapability.from(process.getPurpose())
                : AuthMethodCapability.STEP_UP;
        if (!method.capabilities().contains(capability)
                || (secondFactor && process.getPrimaryMethodType() == methodType)
                || (primary
                        && process.getClaimedMethodType() != null
                        && process.getClaimedMethodType() != methodType)) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_PURPOSE_UNAVAILABLE);
        }
        return process;
    }

    @Override
    public @NotNull AuthenticationProcess requireCurrent(
            @NotNull String authenticationId, @NotNull String browserHash) {
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(authenticationId)
                .singleResult();
        if (instance == null) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_UNAVAILABLE);
        }
        Map<String, Object> variables = runtimeService.getVariables(authenticationId);
        String expectedBrowserHash = (String) variables.get(BROWSER_HASH);
        if (expectedBrowserHash == null
                || !MessageDigest.isEqual(
                        expectedBrowserHash.getBytes(StandardCharsets.UTF_8),
                        browserHash.getBytes(StandardCharsets.UTF_8))) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_UNAVAILABLE);
        }
        Task task = currentTask(authenticationId);
        return snapshot(authenticationId, task, variables);
    }

    @Override
    public @NotNull Session session(
            @NotNull String authenticationId, @NotNull String browserHash) {
        AuthenticationProcess candidate = requireCurrent(authenticationId, browserHash);
        if (!AuthenticationProcessDefinition.COMPLETION.equals(candidate.getActivityId())
                || (candidate.getPurpose() != AuthenticationPurpose.LOGIN
                        && candidate.getPurpose() != AuthenticationPurpose.REGISTRATION)) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_PURPOSE_UNAVAILABLE);
        }
        return subjectLock.execute(candidate.getSubjectId(), () -> {
            AuthenticationProcess process = requireCurrent(authenticationId, browserHash);
            AuthMethodVerification current = proofReviewer.requireCurrent(process);
            completeAuthentication(process);
            Session session = sessionService.create(
                    current.getAccount().getAccountId(), process.getDeviceId(), process.getTerminalName());
            log.info(
                    "认证已创建 Session | authenticationId={} purpose={} subjectId={} accountId={}",
                    authenticationId,
                    process.getPurpose(),
                    process.getSubjectId(),
                    process.getAccountId());
            return session;
        });
    }

    @Override
    public void authorizeStepUp(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull Subject subject,
            @NotNull Account account) {
        if (!subject.getSubjectId().equals(account.getSubjectId())) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
        }
        subjectLock.execute(subject.getSubjectId(), () -> {
            AuthenticationProcess process = requireCurrent(authenticationId, browserHash);
            if (!AuthenticationProcessDefinition.COMPLETION.equals(process.getActivityId())
                    || process.getPurpose() != AuthenticationPurpose.STEP_UP
                    || !subject.getSubjectId().equals(process.getSubjectId())
                    || !account.getAccountId().equals(process.getAccountId())) {
                throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
            }
            AuthMethodVerification current = proofReviewer.requireCurrent(process);
            if (!subject.getSubjectId().equals(current.getSubject().getSubjectId())
                    || !account.getAccountId().equals(current.getAccount().getAccountId())) {
                throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
            }
            log.info(
                    "STEP_UP 认证已授权 | authenticationId={} subjectId={} accountId={} methodType={} expiresAt={}",
                    authenticationId,
                    process.getSubjectId(),
                    process.getAccountId(),
                    process.getPrimaryMethodType(),
                    process.getExpiresAt());
            return null;
        });
    }

    @Override
    public @NotNull AuthenticationProcess completeLinking(
            @NotNull String authenticationId, @NotNull String browserHash) {
        AuthenticationProcess candidate = requireCurrent(authenticationId, browserHash);
        if (!AuthenticationProcessDefinition.COMPLETION.equals(candidate.getActivityId())
                || candidate.getPurpose() != AuthenticationPurpose.LINKING) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_PURPOSE_UNAVAILABLE);
        }
        return subjectLock.execute(candidate.getSubjectId(), () -> {
            AuthenticationProcess process = requireCurrent(authenticationId, browserHash);
            proofReviewer.requireCurrent(process);
            completeAuthentication(process);
            process.setActivityId("completed");
            log.info(
                    "LINKING 认证已完成 | authenticationId={} subjectId={} accountId={} methodType={}",
                    authenticationId,
                    process.getSubjectId(),
                    process.getAccountId(),
                    process.getPrimaryMethodType());
            return process;
        });
    }

    @Override
    public @NotNull ConsumedAccountRecovery consumeRecovery(
            @NotNull String authenticationId, @NotNull String browserHash) {
        AuthenticationProcess candidate = requireCurrent(authenticationId, browserHash);
        if (!AuthenticationProcessDefinition.COMPLETION.equals(candidate.getActivityId())
                || candidate.getPurpose() != AuthenticationPurpose.RECOVERY) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_PURPOSE_UNAVAILABLE);
        }
        return subjectLock.execute(candidate.getSubjectId(), () -> {
            AuthenticationProcess process = requireCurrent(authenticationId, browserHash);
            AuthMethodVerification current = proofReviewer.requireCurrent(process);
            completeAuthentication(process);
            log.info(
                    "RECOVERY 认证已消费 | authenticationId={} subjectId={} accountId={} methodType={}",
                    authenticationId,
                    process.getSubjectId(),
                    process.getAccountId(),
                    process.getPrimaryMethodType());
            return new ConsumedAccountRecovery(current.getSubject(), current.getAccount());
        });
    }

    private @NotNull AuthenticationProcess start(
            @NotNull AuthenticationPurpose purpose,
            @NotNull AccountScopeType scopeType,
            @Nullable Long scopeId,
            @NotNull String browserHash,
            @Nullable String deviceId,
            @Nullable String terminalName,
            @Nullable Long subjectId,
            @Nullable Long accountId) {
        Instant now = Instant.now();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(PURPOSE, purpose.value());
        variables.put(SCOPE_TYPE, scopeType.value());
        if (scopeId != null) {
            variables.put(SCOPE_ID, scopeId);
        }
        variables.put(BROWSER_HASH, browserHash);
        if (deviceId != null) {
            variables.put(DEVICE_ID, deviceId);
        }
        if (terminalName != null) {
            variables.put(TERMINAL_NAME, terminalName);
        }
        if (subjectId != null) {
            variables.put(SUBJECT_ID, subjectId);
            variables.put(ACCOUNT_ID, accountId);
        }
        variables.put(SECOND_FACTOR_REQUIRED, false);
        variables.put(CREATED_AT, Date.from(now));
        var ttl = purpose == AuthenticationPurpose.STEP_UP ? properties.getStepUpTtl() : properties.getTtl();
        variables.put(EXPIRES_AT, Date.from(now.plus(ttl)));
        variables.put(AUTHENTICATION_TTL, ttl.toString());
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                AuthenticationProcessDefinition.PROCESS_KEY, variables);
        AuthenticationProcess process = requireCurrent(instance.getId(), browserHash);
        log.info(
                "认证已创建 | authenticationId={} activityId={} purpose={} scopeType={} subjectId={} accountId={}",
                process.getAuthenticationId(),
                process.getActivityId(),
                process.getPurpose(),
                process.getScopeType(),
                process.getSubjectId(),
                process.getAccountId());
        return process;
    }

    private @NotNull Task currentTask(@NotNull String authenticationId) {
        Task task = taskService.createTaskQuery()
                .processInstanceId(authenticationId)
                .active()
                .singleResult();
        if (task == null) {
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_UNAVAILABLE);
        }
        return task;
    }

    private void completeTask(
            @NotNull Task task,
            @NotNull Map<String, Object> variables,
            @NotNull AuthenticationProcess process,
            @NotNull AuthMethodType methodType) {
        try {
            taskService.complete(task.getId(), variables);
        } catch (FlowableOptimisticLockingException exception) {
            log.info(
                    "认证方式提交发生并发冲突 | authenticationId={} activityId={} purpose={} methodType={}",
                    process.getAuthenticationId(),
                    process.getActivityId(),
                    process.getPurpose(),
                    methodType);
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
        }
    }

    private void completeAuthentication(@NotNull AuthenticationProcess process) {
        try {
            completionTransaction.executeWithoutResult(status -> taskService.complete(
                    currentTask(process.getAuthenticationId()).getId()));
        } catch (FlowableOptimisticLockingException exception) {
            log.info(
                    "认证完成发生并发冲突 | authenticationId={} activityId={} purpose={} subjectId={} accountId={}",
                    process.getAuthenticationId(),
                    process.getActivityId(),
                    process.getPurpose(),
                    process.getSubjectId(),
                    process.getAccountId());
            throw new BizException(AuthenticationErrorCode.AUTHENTICATION_CHANGED);
        }
    }

    private @NotNull AuthenticationProcess snapshot(
            @NotNull String authenticationId,
            @NotNull Task task,
            @NotNull Map<String, Object> variables) {
        return new AuthenticationProcess()
                .setAuthenticationId(authenticationId)
                .setPurpose(BaseEnum.fromValue(
                        AuthenticationPurpose.class, (String) variables.get(PURPOSE)))
                .setActivityId(task.getTaskDefinitionKey())
                .setScopeType(BaseEnum.fromValue(AccountScopeType.class, (String) variables.get(SCOPE_TYPE)))
                .setScopeId((Long) variables.get(SCOPE_ID))
                .setBrowserHash((String) variables.get(BROWSER_HASH))
                .setDeviceId((String) variables.get(DEVICE_ID))
                .setTerminalName((String) variables.get(TERMINAL_NAME))
                .setSubjectId((Long) variables.get(SUBJECT_ID))
                .setAccountId((Long) variables.get(ACCOUNT_ID))
                .setClaimedMethodType(task.getAssignee() == null
                        ? null
                        : BaseEnum.fromValue(AuthMethodType.class, task.getAssignee()))
                .setPrimaryMethodType(enumValue(AuthMethodType.class, variables.get(PRIMARY_METHOD_TYPE)))
                .setPrimaryProofId((String) variables.get(PRIMARY_PROOF_ID))
                .setPrimaryProofVersion((Long) variables.get(PRIMARY_PROOF_VERSION))
                .setSecondFactorRequired(Boolean.TRUE.equals(variables.get(SECOND_FACTOR_REQUIRED)))
                .setSecondFactorMethodType(enumValue(AuthMethodType.class, variables.get(SECOND_FACTOR_METHOD_TYPE)))
                .setSecondFactorProofId((String) variables.get(SECOND_FACTOR_PROOF_ID))
                .setSecondFactorProofVersion((Long) variables.get(SECOND_FACTOR_PROOF_VERSION))
                .setCreatedAt(((Date) variables.get(CREATED_AT)).toInstant())
                .setExpiresAt(((Date) variables.get(EXPIRES_AT)).toInstant());
    }

    private static <E extends Enum<E> & BaseEnum> @Nullable E enumValue(
            @NotNull Class<E> enumType, @Nullable Object value) {
        return value == null ? null : BaseEnum.fromValue(enumType, value.toString());
    }
}
