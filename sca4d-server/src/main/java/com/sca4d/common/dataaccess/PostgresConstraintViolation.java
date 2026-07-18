package com.sca4d.common.dataaccess;

import org.jetbrains.annotations.NotNull;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;

/** 精确识别 Spring 异常链中的 PostgreSQL 约束冲突。 */
public final class PostgresConstraintViolation {

    private PostgresConstraintViolation() {
    }

    public static boolean matches(
            @NotNull DataIntegrityViolationException exception, @NotNull String sqlState, @NotNull String constraint) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof PSQLException sqlException
                    && sqlState.equals(sqlException.getSQLState())
                    && sqlException.getServerErrorMessage() != null
                    && constraint.equals(sqlException.getServerErrorMessage().getConstraint())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
