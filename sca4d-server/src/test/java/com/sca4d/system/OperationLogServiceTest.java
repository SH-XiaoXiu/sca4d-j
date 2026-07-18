package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.audit.AuditActor;
import com.sca4d.common.audit.AuditCodec;
import com.sca4d.common.audit.AuditOperationCatalog;
import com.sca4d.common.audit.AuditPayloadRegistry;
import com.sca4d.common.audit.AuditRecord;
import com.sca4d.common.audit.AuditResult;
import com.sca4d.common.audit.AuditTarget;
import com.sca4d.common.audit.RawAuditPayload;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.common.resource.ResourceNavigationState;
import com.sca4d.common.resource.ResourceReference;
import com.sca4d.common.resource.ResourceReferenceKey;
import com.sca4d.common.resource.ResourceReferenceResolver;
import com.sca4d.config.properties.Sca4djAuditProperties;
import com.sca4d.entity.SysOperationLogEntity;
import com.sca4d.iam.resource.IamResourceTypes;
import com.sca4d.mapper.SysOperationLogMapper;
import com.sca4d.system.audit.OperationLog;
import com.sca4d.system.audit.SystemAuditOperations;
import com.sca4d.system.repository.OperationLogRepository;
import com.sca4d.system.service.DefaultOperationLogService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 验证操作日志 JSON 解码、本地化和未找到处理。 */
class OperationLogServiceTest {

    private final AuditCodec auditCodec = new AuditCodec(
            new ObjectMapper(),
            new AuditPayloadRegistry(List.of()),
            new Sca4djAuditProperties());

    @Test
    void shouldPageOperationLogsAsDomainModel() {
        SysOperationLogMapper mapper = mock(SysOperationLogMapper.class);
        OperationLogService service = service(mapper);
        Page<SysOperationLogEntity> mapperPage = new Page<>(2, 10);
        mapperPage.setRecords(List.of(entity(1L)));
        mapperPage.setTotalRow(1);
        when(mapper.paginate(any(Number.class), any(Number.class), any(QueryWrapper.class))).thenReturn(mapperPage);

        var page = service.page(null, null, null, null, null, null, null, null, null, null, 2, 10);

        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        OperationLog log = page.getRecords().getFirst();
        assertThat(log.getId()).isEqualTo(1L);
        assertThat(log.getAudit().getEnvelope().getPayloadType()).isEqualTo("raw");
        assertThat(log.getModuleName()).isEqualTo("系统字典");
        assertThat(log.getActionName()).isEqualTo("创建");
        assertThat(log.getSummary()).isEqualTo("创建了系统字典");
    }

    @Test
    void shouldThrowWhenDetailMissing() {
        OperationLogService service = service(mock(SysOperationLogMapper.class));

        assertThatThrownBy(() -> service.detail(404L))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.audit.log-not-found"));
    }

    @Test
    void shouldPreserveTargetLabelSnapshotAfterResourceDeletion() {
        SysOperationLogMapper mapper = mock(SysOperationLogMapper.class);
        SysOperationLogEntity entity = entity(2L).setTargetType("dictionary").setTargetId("20");
        AuditRecord record = new AuditRecord();
        AuditTarget target = new AuditTarget("dictionary", "20");
        target.setLabel("订单状态");
        record.setTarget(target);
        record.setPayload(new RawAuditPayload(Map.of()));
        entity.setDetailJson(auditCodec.encode(record));
        when(mapper.findOperationLogById(2L)).thenReturn(entity);
        ResourceReferenceKey key = new ResourceReferenceKey("dictionary", "20");
        ResourceReference deleted = new ResourceReference(
                key, "已删除", null, ResourceNavigationState.DELETED, null, "已删除");

        OperationLog log = service(mapper, keys -> Map.of(key, deleted)).detail(2L);

        assertThat(log.getTargetReference().getLabel()).isEqualTo("订单状态");
        assertThat(log.getTargetReference().getNavigationMessage()).isEqualTo("已删除");
    }

    @Test
    void shouldPreserveActorSubjectSnapshotAfterSubjectDeletion() {
        SysOperationLogMapper mapper = mock(SysOperationLogMapper.class);
        SysOperationLogEntity entity = entity(3L).setSubjectId(10L);
        AuditRecord record = new AuditRecord();
        AuditActor actor = new AuditActor();
        actor.setSubjectDisplayName("历史操作者");
        record.setActor(actor);
        record.setPayload(new RawAuditPayload(Map.of()));
        entity.setDetailJson(auditCodec.encode(record));
        when(mapper.findOperationLogById(3L)).thenReturn(entity);
        ResourceReferenceKey key = new ResourceReferenceKey(IamResourceTypes.SUBJECT, "10");
        ResourceReference deleted = new ResourceReference(
                key, null, null, ResourceNavigationState.DELETED, null, "已删除");

        OperationLog log = service(mapper, keys -> Map.of(key, deleted)).detail(3L);

        assertThat(log.getActorReference().getLabel()).isEqualTo("历史操作者");
        assertThat(log.getActorReference().getNavigationMessage()).isEqualTo("已删除");
    }

    @Test
    void shouldNotInventDeletedTargetLabelForLegacyAuditWithoutSnapshot() {
        SysOperationLogMapper mapper = mock(SysOperationLogMapper.class);
        SysOperationLogEntity entity = entity(4L).setTargetType("dictionary").setTargetId("20");
        when(mapper.findOperationLogById(4L)).thenReturn(entity);
        ResourceReferenceKey key = new ResourceReferenceKey("dictionary", "20");
        ResourceReference deleted = new ResourceReference(
                key, null, null, ResourceNavigationState.DELETED, null, "已删除");

        OperationLog log = service(mapper, keys -> Map.of(key, deleted)).detail(4L);

        assertThat(log.getTargetReference().getLabel()).isNull();
        assertThat(log.getTargetReference().getNavigationMessage()).isEqualTo("已删除");
    }

    @Test
    void shouldReturnLocalizedOperationCatalog() {
        OperationLogService service = service(mock(SysOperationLogMapper.class));

        var catalog = service.catalog();

        assertThat(catalog).extracting(item -> item.getCode())
                .containsExactly(
                        "system.dictionaries",
                        "system.menus",
                        "system.mail-senders",
                        "system.mail-templates",
                        "system.identity-providers");
        assertThat(catalog.getFirst().getName()).isEqualTo("系统字典");
        assertThat(catalog.getFirst().getActions()).hasSize(8);
        assertThat(catalog.getFirst().getActions().getFirst().getName()).isEqualTo("创建");
        assertThat(catalog.get(1).getName()).isEqualTo("系统菜单");
        assertThat(catalog.get(1).getActions()).hasSize(4);
        assertThat(catalog.get(2).getName()).isEqualTo("邮件发送配置");
        assertThat(catalog.get(2).getActions()).hasSize(5);
        assertThat(catalog.get(2).getActions().get(3).getName()).isEqualTo("测试连接");
        assertThat(catalog.get(3).getName()).isEqualTo("邮件模板");
        assertThat(catalog.get(3).getActions()).hasSize(5);
        assertThat(catalog.get(3).getActions().get(3).getName()).isEqualTo("测试发送");
        assertThat(catalog.get(4).getName()).isEqualTo("全局外部身份源");
        assertThat(catalog.get(4).getActions()).hasSize(5);
        assertThat(catalog.get(4).getActions().get(3).getName()).isEqualTo("测试连接");
    }

    /**
     * 创建只注册系统模块审计目录的操作日志服务。
     *
     * @param mapper 测试控制分页和详情结果的操作日志 Mapper
     * @return 使用真实 Repository、Codec、动作目录和本地化消息解析器的服务
     */
    private @NotNull OperationLogService service(@NotNull SysOperationLogMapper mapper) {
        return service(mapper, keys -> Map.of());
    }

    /** 创建使用指定资源引用解析结果的操作日志服务。 */
    private @NotNull OperationLogService service(
            @NotNull SysOperationLogMapper mapper, @NotNull ResourceReferenceResolver resolver) {
        return new DefaultOperationLogService(
                new OperationLogRepository(mapper),
                auditCodec,
                new AuditOperationCatalog(List.of(new SystemAuditOperations())),
                LocalizedMessagesFixture.create(),
                resolver);
    }

    /**
     * 创建包含可解码 raw 审计载荷的操作日志实体。
     *
     * @param id 操作日志主键，同时写入测试载荷
     * @return 可供 Repository 分页结果转换的实体
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
