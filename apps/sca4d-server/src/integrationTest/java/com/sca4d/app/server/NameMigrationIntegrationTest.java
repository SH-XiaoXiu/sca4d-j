package com.sca4d.app.server;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 验证名称迁移的新库安装、旧库升级、Quartz 外键和归一化冲突回滚。 */
@Testcontainers
class NameMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private JdbcTemplate jdbc;

    /** 每个用例使用容器内独立 schema，不连接开发数据库。 */
    @BeforeEach
    void resetDatabase() {
        jdbc = new JdbcTemplate(
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("DROP SCHEMA IF EXISTS job CASCADE");
        jdbc.execute("DROP SCHEMA IF EXISTS quartz CASCADE");
        jdbc.execute("CREATE SCHEMA public");
    }

    @Test
    void installsAnEmptyDatabaseWithAllMigrations() {
        flyway(null).migrate();
        assertThat(
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_schema='job' AND table_name='job_run'",
                        String.class))
                .contains("app_name")
                .doesNotContain("runtime_namespace");
        assertThat(
                jdbc.queryForObject(
                        "SELECT count(*) FROM pg_constraint WHERE connamespace='quartz'::regnamespace AND contype='f' AND condeferrable",
                        Integer.class))
                .isZero();
    }

    @Test
    void upgradesExistingNamesAndPreservesQuartzReferences() {
        flyway("10").migrate();
        jdbc.update(
                "INSERT INTO iam_principal(id,principal_type,status) VALUES (100,'iam.platform-principal','ACTIVE')");
        jdbc.update(
                "INSERT INTO job.job_run(id,runtime_namespace,job_key,job_definition_version,job_kind,status) VALUES (200,'sca4d-server','TEST/JOB',1,'DIRECT','QUEUED')");
        jdbc.update(
                "INSERT INTO quartz.qrtz_job_details(sched_name,job_name,job_group,job_class_name,is_durable,is_nonconcurrent,is_update_data,requests_recovery) VALUES ('sca4d-server:sca4dj:scheduling:quartz','TEST/JOB','sca4dj-code','org.quartz.Job',true,false,false,false)");
        jdbc.update(
                "INSERT INTO quartz.qrtz_triggers(sched_name,trigger_name,trigger_group,job_name,job_group,trigger_state,trigger_type,start_time,end_time) VALUES ('sca4d-server:sca4dj:scheduling:quartz','TEST/JOB','sca4dj-code','TEST/JOB','sca4dj-code','WAITING','SIMPLE',0,0)");
        jdbc.update(
                "INSERT INTO quartz.qrtz_simple_triggers(sched_name,trigger_name,trigger_group,repeat_count,repeat_interval,times_triggered) VALUES ('sca4d-server:sca4dj:scheduling:quartz','TEST/JOB','sca4dj-code',0,1000,0)");
        flyway(null).migrate();
        assertThat(jdbc.queryForObject("SELECT principal_type FROM iam_principal WHERE id=100", String.class))
                .isEqualTo("IAM.PLATFORM-PRINCIPAL");
        assertThat(jdbc.queryForObject("SELECT app_name FROM job.job_run WHERE id=200", String.class))
                .isEqualTo("SCA4D_SERVER");
        assertThat(jdbc.queryForObject("SELECT sched_name FROM quartz.qrtz_simple_triggers", String.class))
                .isEqualTo("SCA4D_SERVER:SCHEDULING:QUARTZ");
        assertThat(
                jdbc.queryForObject(
                        "SELECT count(*) FROM pg_constraint WHERE connamespace='quartz'::regnamespace AND contype='f' AND condeferrable",
                        Integer.class))
                .isZero();
    }

    @Test
    void rejectsAmbiguousTypesWithoutChangingExistingRows() {
        flyway("10").migrate();
        jdbc.update(
                "INSERT INTO iam_principal(id,principal_type,status) VALUES (100,'iam.platform-principal','ACTIVE'),(101,'IAM.PLATFORM-PRINCIPAL','ACTIVE')");
        assertThatThrownBy(() -> flyway(null).migrate()).hasStackTraceContaining("Principal 类型归一化后重名");
        assertThat(jdbc.queryForObject("SELECT principal_type FROM iam_principal WHERE id=100", String.class))
                .isEqualTo("iam.platform-principal");
        assertThat(
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_schema='job' AND table_name='job_run'",
                        String.class))
                .contains("runtime_namespace")
                .doesNotContain("app_name");
    }

    /** 创建主库迁移器；target 为空时执行全部版本。 */
    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        return configuration.load();
    }
}
