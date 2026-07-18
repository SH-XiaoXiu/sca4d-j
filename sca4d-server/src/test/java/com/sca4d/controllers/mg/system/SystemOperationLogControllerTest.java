package com.sca4d.controllers.mg.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.audit.AuditCodec;
import com.sca4d.common.audit.AuditOperationCatalog;
import com.sca4d.common.audit.AuditPayloadRegistry;
import com.sca4d.common.audit.AuditRecord;
import com.sca4d.common.audit.AuditResult;
import com.sca4d.common.audit.RawAuditPayload;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.exception.ResponseCode;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.config.properties.Sca4djAuditProperties;
import com.sca4d.entity.SysOperationLogEntity;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.mapper.SysOperationLogMapper;
import com.sca4d.system.SystemPermissions;
import com.sca4d.system.audit.SystemAuditOperations;
import com.sca4d.system.repository.OperationLogRepository;
import com.sca4d.system.request.OperationLogPageRequest;
import com.sca4d.system.service.DefaultOperationLogService;
import com.sca4d.system.view.AuditPayloadViewRegistry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 验证操作日志分页和详情接口转换为管理端 View。 */
class SystemOperationLogControllerTest {

    private final AuditCodec auditCodec = new AuditCodec(
            new ObjectMapper(),
            new AuditPayloadRegistry(List.of()),
            new Sca4djAuditProperties());

    @Test
    void shouldDeclareRestfulRouteAndPermission() {
        RequestMapping mapping = SystemOperationLogController.class.getAnnotation(RequestMapping.class);
        AuthPrincipal principal = SystemOperationLogController.class.getAnnotation(AuthPrincipal.class);
        RequirePermission permission = SystemOperationLogController.class.getAnnotation(RequirePermission.class);

        assertThat(mapping.value()).containsExactly("/mg/system/operation-logs");
        assertThat(principal.types()).containsExactly(PrincipalType.PLATFORM);
        assertThat(principal.selection()).isEqualTo(PlatformPrincipalSelection.class);
        assertThat(permission.value()).containsExactly(SystemPermissions.OPERATION_LOGS_READ);
    }

    @Test
    void shouldReturnPagedOperationLogViews() {
        SysOperationLogMapper mapper = mock(SysOperationLogMapper.class);
        Page<SysOperationLogEntity> mapperPage = new Page<>(1, 20);
        mapperPage.setRecords(List.of(entity(1L)));
        mapperPage.setTotalRow(1);
        when(mapper.paginate(any(Number.class), any(Number.class), any(QueryWrapper.class))).thenReturn(mapperPage);
        SystemOperationLogController controller = new SystemOperationLogController(
                new DefaultOperationLogService(
                        new OperationLogRepository(mapper),
                        auditCodec,
                        new AuditOperationCatalog(List.of(new SystemAuditOperations())),
                        LocalizedMessagesFixture.create(),
                        keys -> Map.of()),
                new AuditPayloadViewRegistry(List.of()));

        var response = controller.page(new OperationLogPageRequest());

        assertThat(response.getCode()).isEqualTo(ResponseCode.SUCCESS.getCode());
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getRecords()).hasSize(1);
        assertThat(response.getData().getRecords().getFirst().getPayloadType()).isEqualTo("raw");
        assertThat(response.getData().getRecords().getFirst().getSummary()).isEqualTo("创建了系统字典");
    }

    /**
     * 创建可被 Controller 分页响应转换为操作日志 View 的实体。
     *
     * @param id 操作日志主键，同时写入 raw 审计载荷
     * @return 带固定审计元数据的操作日志实体
     */
    private @NotNull SysOperationLogEntity entity(@NotNull Long id) {
        AuditRecord record = new AuditRecord();
        record.setPayload(new RawAuditPayload(Map.of("id", id)));
        return new SysOperationLogEntity()
                .setId(id)
                .setSubjectId(1L)
                .setPrincipalId(10L)
                .setModuleCode("system.dictionaries")
                .setActionCode("create")
                .setResult(AuditResult.SUCCESS)
                .setRequestUri("/mg/system/operation-logs")
                .setDetailJson(auditCodec.encode(record))
                .setCreatedAt(OffsetDateTime.parse("2026-07-05T10:00:00+08:00"));
    }
}
