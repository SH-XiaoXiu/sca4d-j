package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.SysOperationLogEntity;
import com.sca4d.system.audit.OperationLogOperatorOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 系统操作日志 MyBatis 查询 Mapper。
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLogEntity> {

    /**
     * 写入系统操作日志。
     *
     * @param entity 系统操作日志实体
     * @param detailJson 审计详情 JSON
     * @return 写入行数
     */
    int insertOperationLog(
            @Param("entity") @NotNull SysOperationLogEntity entity, @Param("detailJson") @NotNull String detailJson);

    /**
     * 按主键查询系统操作日志。
     *
     * @param id 操作日志主键
     * @return 系统操作日志实体
     */
    @Nullable
    SysOperationLogEntity findOperationLogById(@Param("id") @NotNull Long id);

    /**
     * 统计符合搜索条件且拥有名称快照的历史操作人数量。
     *
     * @param search Subject 展示名称模糊搜索；缺失时统计全部
     * @return 去重后的 Subject 数量
     */
    long countOperationLogOperators(@Param("search") @Nullable String search);

    /**
     * 分页读取操作日志中每个 Subject 最新的名称快照。
     *
     * @param search Subject 展示名称模糊搜索；缺失时读取全部
     * @param offset 从零开始的分页偏移量
     * @param limit 本次最多返回数量
     * @return 按展示名称和 Subject 排序的操作人选项
     */
    @NotNull
    List<OperationLogOperatorOption> selectOperationLogOperators(
            @Param("search") @Nullable String search,
            @Param("offset") long offset,
            @Param("limit") long limit);
}
