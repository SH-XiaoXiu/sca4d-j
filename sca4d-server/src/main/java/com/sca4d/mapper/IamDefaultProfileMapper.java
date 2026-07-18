package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.IamDefaultProfileEntity;
import com.sca4d.iam.profile.repository.DefaultProfileRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** DEFAULT Profile 的 SQL Mapper。 */
@Mapper
public interface IamDefaultProfileMapper extends BaseMapper<IamDefaultProfileEntity> {

    /** 按 Subject 查询其未删除 DEFAULT Principal 的默认资料。 */
    @Nullable
    DefaultProfileRow selectBySubjectId(@Param("subjectId") @NotNull Long subjectId);

    /** 批量查询多个 Subject 的 DEFAULT Profile 头像资料。 */
    @NotNull
    List<DefaultProfileRow> selectBySubjectIds(@Param("subjectIds") @NotNull List<Long> subjectIds);

    /** 按 Subject 和版本条件替换 DEFAULT Profile 头像。 */
    int updateAvatar(
            @Param("subjectId") @NotNull Long subjectId,
            @Param("avatarFileId") @Nullable Long avatarFileId,
            @Param("version") @NotNull Long version);

    /** 按 Subject 和版本条件逻辑删除 DEFAULT Profile。 */
    int isolateBySubjectId(
            @Param("subjectId") @NotNull Long subjectId, @Param("version") @NotNull Long version);

    /** 硬删除已经逻辑删除的 DEFAULT Profile。 */
    int purgeBySubjectId(@Param("subjectId") @NotNull Long subjectId);
}
