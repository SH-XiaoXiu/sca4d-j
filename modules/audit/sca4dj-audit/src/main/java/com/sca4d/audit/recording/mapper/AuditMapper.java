package com.sca4d.audit.recording.mapper;

import com.sca4d.audit.query.AuditQuery;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 读写 `audit_record` 的审计列，并提供按条件统计、分页和单条查询。 */
public interface AuditMapper {
    /** @param row 待追加的审计列值 @return 影响行数 */
    int insert(@Param("row") @NotNull EncodedAuditEntry row);

    /** 消息重投时按主键去重，不覆盖首次写入的审计事实。 */
    int insertDelivery(@Param("row") @NotNull EncodedAuditEntry row);

    /** @param id 审计记录标识 @return 对应列值，不存在时为空 */
    @Nullable
    EncodedAuditEntry selectById(@Param("id") long id);

    /** @param query 已校验查询条件 @return 满足条件的记录总数 */
    long count(@Param("query") @NotNull AuditQuery query);

    /** @param query 已校验查询条件 @return 按发生时间和主键倒序的一页记录 */
    @NotNull
    List<@NotNull EncodedAuditEntry> selectPage(@Param("query") @NotNull AuditQuery query);
}
