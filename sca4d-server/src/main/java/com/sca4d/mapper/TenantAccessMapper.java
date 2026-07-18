package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.TenantAccessGrantEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 租户业务模块 ACL 的单表持久化 Mapper。 */
@Mapper
public interface TenantAccessMapper extends BaseMapper<TenantAccessGrantEntity> {

    /**
     * 批量写入完整 ACL 快照并让 PostgreSQL 生成共用的创建时间。
     *
     * @param grants 已分配主键且完成业务模块校验的规则
     * @return 实际写入行数
     */
    int insertGrants(@Param("grants") @NotNull List<TenantAccessGrantEntity> grants);
}
