package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.IamContactEntity;
import com.sca4d.iam.contact.ContactType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

/** Contact 单表 Mapper。 */
@Mapper
public interface IamContactMapper extends BaseMapper<IamContactEntity> {

    /**
     * 逻辑删除 Subject 指定类型的当前 Contact。
     *
     * @param subjectId Contact 所属 Subject
     * @param type EMAIL 或 PHONE
     */
    void deleteCurrent(
            @Param("subjectId") @NotNull Long subjectId,
            @Param("type") @NotNull ContactType type);
}
