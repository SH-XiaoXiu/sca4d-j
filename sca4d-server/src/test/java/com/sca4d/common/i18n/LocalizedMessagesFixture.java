package com.sca4d.common.i18n;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.support.ResourceBundleMessageSource;

/** 测试中使用与生产相同资源包创建本地化消息入口的夹具。 */
public final class LocalizedMessagesFixture {
    private static final String[] BASENAMES = {
            "i18n/common",
            "i18n/error",
            "i18n/permission",
            "i18n/audit",
            "i18n/dictionary",
            "i18n/resource",
            "i18n/navigation"
    };

    /** 禁止实例化测试夹具。 */
    private LocalizedMessagesFixture() {
    }

    /**
     * 创建读取生产环境全部 classpath 消息资源包的本地化入口。
     *
     * @return 使用 UTF-8 和生产 basename 的消息解析器
     */
    public static @NotNull LocalizedMessages create() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames(BASENAMES);
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return new LocalizedMessages(source);
    }
}
