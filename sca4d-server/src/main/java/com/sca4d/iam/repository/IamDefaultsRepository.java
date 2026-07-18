package com.sca4d.iam.repository;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** 维护代码权限同步后必须存在的 IAM 静态角色、默认授权和关系数据。 */
@Repository
public class IamDefaultsRepository {

    private static final long SYNCHRONIZATION_LOCK_KEY = 0x534341344449414dL;
    private static final ClassPathResource DEFAULTS_SCRIPT = new ClassPathResource("db/seed/iam-defaults.sql");

    private final SqlSessionFactory sqlSessionFactory;

    /**
     * 创建 IAM 默认数据仓储。
     *
     * @param sqlSessionFactory 当前业务事务使用的 MyBatis Session 工厂
     */
    public IamDefaultsRepository(@NotNull SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /**
     * 在当前事务结束前独占 IAM 启动同步。
     *
     * <p>锁由 PostgreSQL 事务持有并自动释放，多个应用实例同时启动时只有一个实例可以修改权限目录和默认关系。
     */
    public void lockSynchronization() {
        SqlSession sqlSession = SqlSessionUtils.getSqlSession(sqlSessionFactory);
        if (!SqlSessionUtils.isSqlSessionTransactional(sqlSession, sqlSessionFactory)) {
            SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
            throw new IllegalStateException("IAM 启动同步必须在 MyBatis 事务中执行");
        }
        Connection connection = sqlSession.getConnection();
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, SYNCHRONIZATION_LOCK_KEY);
            statement.execute();
        } catch (SQLException exception) {
            throw new IllegalStateException("IAM 启动同步锁获取失败", exception);
        } finally {
            SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
        }
    }

    /**
     * 在权限目录同步完成后执行幂等 IAM 默认数据脚本。
     *
     * <p>脚本使用当前 Spring 管理的 MyBatis 事务连接；任一语句失败会使权限目录、默认角色和关系修改一起回滚。
     */
    public void synchronizeDefaults() {
        SqlSession sqlSession = SqlSessionUtils.getSqlSession(sqlSessionFactory);
        if (!SqlSessionUtils.isSqlSessionTransactional(sqlSession, sqlSessionFactory)) {
            SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
            throw new IllegalStateException("IAM 默认数据同步必须在 MyBatis 事务中执行");
        }
        Connection connection = sqlSession.getConnection();
        try {
            ScriptUtils.executeSqlScript(connection, DEFAULTS_SCRIPT);
        } finally {
            SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
        }
    }
}
