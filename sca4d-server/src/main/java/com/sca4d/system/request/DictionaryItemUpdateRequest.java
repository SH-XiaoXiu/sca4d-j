package com.sca4d.system.request;

import com.sca4d.common.concurrent.VersionedRequest;
import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.common.i18n.SupportedLanguage;
import com.sca4d.common.validation.Required;
import com.sca4d.system.DictionaryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/** 修改可配置字典项内容和启用状态的请求。 */
@Data
public class DictionaryItemUpdateRequest implements VersionedRequest {

    @NotBlank
    @Size(max = 128)
    private String value;

    @NotEmpty
    private Map<@SupportedLanguage String, @Size(max = 128) String> labels;

    @Required
    private DictionaryTone tone;

    @Required
    private Boolean selectable;

    @Required
    private DictionaryStatus status;

    @Required
    private Long version;
}
