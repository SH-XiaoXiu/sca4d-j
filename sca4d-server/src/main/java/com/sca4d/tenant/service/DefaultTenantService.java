package com.sca4d.tenant.service;

import com.sca4d.authentication.AuthenticationClientStateTargets;
import com.sca4d.authentication.SessionService;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.validation.ValidationPatterns;
import com.sca4d.common.web.ClientState;
import com.sca4d.common.web.PageData;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.iam.tenant.TenantStatus;
import com.sca4d.iam.tenant.TenantType;
import com.sca4d.system.repository.DictionaryRepository;
import com.sca4d.tenant.Tenant;
import com.sca4d.tenant.TenantErrorCode;
import com.sca4d.tenant.TenantMembership;
import com.sca4d.tenant.TenantPermissions;
import com.sca4d.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 创建、查询、更新、停用和删除租户。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultTenantService implements TenantService {

    private final IamRepository iamRepository;
    private final DictionaryRepository dictionaryRepository;
    private final SessionService sessionService;

    @Override
    public @NotNull PageData<Tenant> page(
            @Nullable String search, @Nullable TenantStatus status, long page, long size) {
        // 搜索词为空白时传入 null，Repository 收到 null 后不添加关键词查询条件。
        return iamRepository.pageTenants(
                StringUtils.hasText(search) ? search.trim() : null, status, page, size);
    }

    @Override
    public @NotNull Tenant require(@NotNull Long tenantId) {
        // 后续状态和成员操作只接受完整租户读取对象，避免对不存在租户执行关系写入。
        Tenant tenant = iamRepository.findTenant(tenantId);
        if (tenant == null) {
            throw new BizException(TenantErrorCode.TENANT_NOT_FOUND);
        }
        return tenant;
    }

    @Override
    public @NotNull Tenant requireActive(@NotNull Long tenantId) {
        Tenant tenant = require(tenantId);
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new BizException(TenantErrorCode.TENANT_UNAVAILABLE);
        }
        return tenant;
    }

    @Override
    public @NotNull Tenant requireActiveByCode(@NotNull String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            throw new BizException(TenantErrorCode.TENANT_CODE_REQUIRED);
        }
        String checkedCode = tenantCode.trim();
        if (checkedCode.length() > 64 || !ValidationPatterns.isBizCode(checkedCode)) {
            throw new BizException(TenantErrorCode.TENANT_CODE_INVALID);
        }
        Tenant tenant = iamRepository.findTenantByCode(checkedCode);
        if (tenant == null) {
            throw new BizException(TenantErrorCode.TENANT_NOT_FOUND);
        }
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new BizException(TenantErrorCode.TENANT_UNAVAILABLE);
        }
        return tenant;
    }

    @Override
    public @NotNull TenantStatus lockForWrite(@NotNull Long tenantId) {
        return iamRepository.lockExistingTenantForWrite(tenantId);
    }

    @Override
    public void lockActiveForWrite(@NotNull Long tenantId) {
        if (lockForWrite(tenantId) != TenantStatus.ACTIVE) {
            throw new BizException(TenantErrorCode.TENANT_UNAVAILABLE);
        }
    }

    @Override
    @Transactional
    public @NotNull Long create(
            @NotNull String code, @NotNull String name, @NotNull TenantType tenantType) {
        // 先转换并检查业务编码，再以数据库永久编码数据作为并发创建的最终来源。
        iamRepository.requireTenantCreationAccess();
        if (!StringUtils.hasText(code)) {
            throw new BizException(TenantErrorCode.TENANT_CODE_REQUIRED);
        }
        String checkedCode = code.trim();
        if (checkedCode.length() > 64 || !ValidationPatterns.isBizCode(checkedCode)) {
            throw new BizException(TenantErrorCode.TENANT_CODE_INVALID);
        }
        if (!StringUtils.hasText(name)) {
            throw new BizException(TenantErrorCode.TENANT_NAME_REQUIRED);
        }
        Long tenantId = iamRepository.createTenant(checkedCode, name.trim(), tenantType);
        iamRepository.createTenantRoleWithPermissions(
                tenantId,
                TenantPermissions.TENANT_ADMIN_ROLE,
                "租户管理员",
                TenantPermissions.MODULE);
        log.info(
                "租户创建已写入事务 | tenantId={} status={} tenantType={}",
                tenantId,
                TenantStatus.ACTIVE,
                tenantType);
        return tenantId;
    }

    @Override
    @Transactional
    public void update(
            @NotNull Long tenantId,
            @NotNull String code,
            @NotNull String name,
            @NotNull Long version) {
        // 先确认租户仍存在并校验编码与名称，再原子修改资料和登记新编码；旧编码永不释放。
        require(tenantId);
        if (!StringUtils.hasText(code)) {
            throw new BizException(TenantErrorCode.TENANT_CODE_REQUIRED);
        }
        String checkedCode = code.trim();
        if (checkedCode.length() > 64 || !ValidationPatterns.isBizCode(checkedCode)) {
            throw new BizException(TenantErrorCode.TENANT_CODE_INVALID);
        }
        if (!StringUtils.hasText(name)) {
            throw new BizException(TenantErrorCode.TENANT_NAME_REQUIRED);
        }
        iamRepository.updateTenant(tenantId, checkedCode, name.trim(), version);
        log.info("租户资料更新已写入事务 | tenantId={} version={}", tenantId, version + 1);
    }

    @Override
    public void changeStatus(
            @NotNull Long tenantId, @NotNull TenantStatus status, @NotNull Long version) {
        // 停用租户只修改租户自身状态；成员 TENANT Principal 保留，选择器会在进入时拒绝已停用租户。
        require(tenantId);
        List<Long> subjectIds = iamRepository.findTenantSubjectIds(tenantId);
        iamRepository.updateTenantStatus(tenantId, status, version);
        sessionService.refreshAfterCommit(subjectIds);
        ClientState.refresh(AuthenticationClientStateTargets.ME);
        log.info(
                "租户状态更新完成 | tenantId={} status={} version={} affectedSubjectCount={}",
                tenantId,
                status,
                version + 1,
                subjectIds.size());
    }

    @Override
    @Transactional
    public void delete(@NotNull Long tenantId, @NotNull Long version) {
        // 删除先确认当前版本，再同步隔离租户、成员 Principal、租户角色和私有字典；任一步失败时整笔事务回滚。
        require(tenantId);
        List<Long> subjectIds = iamRepository.isolateDeletedTenant(tenantId, version);
        dictionaryRepository.isolateTenantDictionaries(tenantId);
        // Session 刷新只使用隔离前一次批量读取的 Subject 集合，提交后不再从已删除 Principal 反查。
        sessionService.refreshAfterCommit(subjectIds);
        ClientState.refresh(AuthenticationClientStateTargets.ME);
        log.info(
                "租户删除已写入事务，关系清理等待后台处理 | tenantId={} affectedSubjectCount={}",
                tenantId,
                subjectIds.size());
    }

    @Override
    public @NotNull List<TenantMembership> memberships(@NotNull Long subjectId) {
        return iamRepository.findTenantMemberships(subjectId);
    }
}
