package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.dictionary.DictionarySource;
import com.sca4d.system.DictionaryKind;
import com.sca4d.system.DictionaryStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * 系统模块维护的字典类型持久化实体。
 *
 * <p>{@code tenantId} 为空时表示平台全局字典；代码枚举固定属于全局范围。{@code nameLabels} 保存可配置字典的
 * JSON 多语言标签，结构由 DictionaryRepository 共用编解码。
 */
@Data
@Accessors(chain = true)
@Table("sys_dict_type")
public class SysDictionaryTypeEntity {

    private Long id;
    private Long tenantId;
    private String code;
    private DictionaryKind kind;
    private DictionarySource source;
    private String nameKey;
    private String nameLabels;
    private DictionaryStatus status;
    private Integer sortOrder;
    private String sourceRef;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
