package com.sca4d.system.request;

import com.sca4d.common.concurrent.IdempotentRequest;
import com.sca4d.common.i18n.SupportedLanguage;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.BizIdMode;
import com.sca4d.common.validation.Required;
import com.sca4d.system.DictionaryKind;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/** 创建业务编码或输入建议字典类型的请求。 */
@Data
public class DictionaryCreateRequest implements IdempotentRequest {

    @Required
    @BizId(mode = BizIdMode.NAMESPACED_CODE, max = 64)
    private String code;

    @Required
    private DictionaryKind kind;

    @NotEmpty
    private Map<@SupportedLanguage String, @Size(max = 128) String> labels;

    private String idempotencyKey;
}
