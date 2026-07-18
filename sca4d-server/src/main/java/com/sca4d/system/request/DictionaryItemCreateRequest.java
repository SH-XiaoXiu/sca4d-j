package com.sca4d.system.request;

import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.common.i18n.SupportedLanguage;
import com.sca4d.common.validation.Required;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/** 在可配置字典中创建业务编码项或输入建议项的请求。 */
@Data
public class DictionaryItemCreateRequest implements IdempotentRequest {

    @NotBlank
    @Size(max = 128)
    private String value;

    @NotEmpty
    private Map<@SupportedLanguage String, @Size(max = 128) String> labels;

    @Required
    private DictionaryTone tone;

    @Required
    private Boolean selectable;

    private String idempotencyKey;
}
