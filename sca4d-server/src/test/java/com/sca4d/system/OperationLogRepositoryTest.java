package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.audit.AuditResult;
import com.sca4d.entity.SysOperationLogEntity;
import com.sca4d.mapper.SysOperationLogMapper;
import com.sca4d.system.audit.OperationLogOperatorOption;
import com.sca4d.system.repository.OperationLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

/** 验证操作日志分页筛选和单条详情查询。 */
class OperationLogRepositoryTest {

    @Test
    void shouldNotBindNullableSearchInsidePostgresqlIsNullExpression() throws Exception {
        String mapperXml;
        try (var input = OperationLogRepositoryTest.class.getResourceAsStream("/mapper/SysOperationLogMapper.xml")) {
            assertThat(input).isNotNull();
            mapperXml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(mapperXml)
                .doesNotContain("#{search} IS NULL")
                .contains("<if test=\"search != null\">")
                .contains("subject_display_name ILIKE '%' || #{search} || '%'");
    }

    @Test
    void shouldBuildTypeSafePageQueryInRepository() {
        SysOperationLogMapper mapper = mock(SysOperationLogMapper.class);
        OperationLogRepository repository = new OperationLogRepository(mapper);
        Page<SysOperationLogEntity> mapperPage = new Page<>(3, 25);
        when(mapper.paginate(eq(3L), eq(25L), any(QueryWrapper.class))).thenReturn(mapperPage);

        repository.page(
                "system",
                "read",
                AuditResult.SUCCESS,
                22L,
                11L,
                "credential",
                "33",
                "trace-1",
                OffsetDateTime.parse("2026-07-01T00:00:00+08:00"),
                OffsetDateTime.parse("2026-07-31T23:59:59+08:00"),
                3,
                25);

        ArgumentCaptor<QueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).paginate(eq(3L), eq(25L), wrapperCaptor.capture());
    }

    @Test
    void shouldRejectUnexpectedAuditInsertAffectedRows() {
        SysOperationLogMapper mapper = mock(SysOperationLogMapper.class);
        OperationLogRepository repository = new OperationLogRepository(mapper);
        SysOperationLogEntity entity = new SysOperationLogEntity().setId(1L);
        when(mapper.insertOperationLog(entity, "{}")).thenReturn(0);

        assertThatThrownBy(() -> repository.appendAudit(entity, "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("操作日志写入行数异常");
    }

    @Test
    void shouldPageHistoricalOperatorSnapshotsWithTwoQueries() {
        SysOperationLogMapper mapper = mock(SysOperationLogMapper.class);
        OperationLogRepository repository = new OperationLogRepository(mapper);
        OperationLogOperatorOption option = new OperationLogOperatorOption()
                .setSubjectId(12L)
                .setSubjectDisplayName("审计管理员");
        when(mapper.countOperationLogOperators("审计")).thenReturn(1L);
        when(mapper.selectOperationLogOperators("审计", 20L, 20L)).thenReturn(List.of(option));

        var page = repository.pageOperatorOptions("  审计  ", 2, 20);

        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).containsExactly(option);
        verify(mapper).countOperationLogOperators("审计");
        verify(mapper).selectOperationLogOperators("审计", 20L, 20L);
    }
}
