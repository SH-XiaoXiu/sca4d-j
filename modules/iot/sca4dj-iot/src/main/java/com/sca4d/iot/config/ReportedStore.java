package com.sca4d.iot.config;

/** latest reported 投影使用的内置存储。 */
public enum ReportedStore {
    /** 保存到 PostgreSQL 主库。 */
    POSTGRESQL,
    /** 保存到带 App 隔离的 Redis。 */
    REDIS
}
