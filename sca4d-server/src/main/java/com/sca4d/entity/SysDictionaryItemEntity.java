package com.sca4d.entity;

import com.mybatisflex.annotation.Table;
import com.sca4d.common.dictionary.DictionarySource;
import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.system.DictionaryStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

/**
 * 系统模块维护的字典项持久化实体。
 *
 * <p>{@code itemValue} 的业务含义由所属字典类型决定：代码枚举和业务编码使用固定值，输入建议使用可直接写入输入框的文本。
 * {@code labelLabels} 保存可配置字典项的 JSON 多语言标签，结构由 DictionaryRepository 共用编解码。
 */
@Data
@Accessors(chain = true)
@Table("sys_dict_item")
public class SysDictionaryItemEntity {

    private Long id;
    private Long dictTypeId;
    private String itemValue;
    private DictionarySource source;
    private String sourceRef;
    private String labelKey;
    private String labelLabels;
    private DictionaryTone tone;
    private Boolean selectable;
    private DictionaryStatus status;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
    private Integer deleted;
}
