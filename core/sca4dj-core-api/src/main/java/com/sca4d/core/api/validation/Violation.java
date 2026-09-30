package com.sca4d.core.api.validation;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;

/** 表示一个输入位置没有满足规则的校验问题（Validation Violation）。 */
public class Violation {

    private final String messageKey;

    private final String path;

    /**
     * 创建校验问题。
     *
     * @param path 出现问题的输入位置
     * @param messageKey 可由应用消息目录解析的消息键
     */
    public Violation(String path, String messageKey) {
        this.path = TextUtils.requireText(path, "校验位置");
        this.messageKey = TextUtils.requireText(messageKey, "校验消息键");
    }

    /**
     * 出现问题的输入位置。
     *
     * @return 出现问题的输入位置
     */
    @Contract(pure = true)
    public String path() {
        return path;
    }

    /**
     * 国际化消息键。
     *
     * @return 国际化消息键
     */
    @Contract(pure = true)
    public String messageKey() {
        return messageKey;
    }

}
