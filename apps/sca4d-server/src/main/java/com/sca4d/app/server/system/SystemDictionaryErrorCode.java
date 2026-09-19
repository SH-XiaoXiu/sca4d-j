package com.sca4d.app.server.system;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;
import org.jetbrains.annotations.NotNull;

/** 定义系统字典管理可以直接反馈给管理端的业务错误。 */
@ErrorCodeRange(start = 3100, end = 3199)
public enum SystemDictionaryErrorCode implements ErrorCode {
    /** 动态字典键已经被代码字典或数据库字典占用。 */
    DICTIONARY_KEY_EXISTS("3100", "error.system-dictionary.dictionary-key-exists"),
    /** 字典内的选项值已经被代码项或数据库项占用。 */
    OPTION_VALUE_EXISTS("3101", "error.system-dictionary.option-value-exists");

    private final @NotNull String code;

    private final @NotNull String messageKey;

    SystemDictionaryErrorCode(@NotNull String code, @NotNull String messageKey) {
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override
    public @NotNull String code() {
        return code;
    }

    @Override
    public @NotNull ErrorCategory category() {
        return ErrorCategory.CONFLICT;
    }

    @Override
    public @NotNull String messageKey() {
        return messageKey;
    }
}
