package com.sca4d.core.spring.i18n;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 读取应用支持的语言标签和默认语言标签。 */
@ConfigurationProperties("sca4dj.i18n")
public class I18nProperties {

    private List<String> supportedLocales = new ArrayList<>();

    private String defaultLocale = "";

    public I18nProperties() {
    }

    /**
     * 读取 Accept-Language 匹配时使用的标签及优先级。
     *
     * @return 应用按优先级支持的 BCP 47 语言标签副本
     */
    public List<String> getSupportedLocales() {
        return List.copyOf(supportedLocales);
    }

    /**
     * 替换应用支持的语言标签及同语言回退次序。
     *
     * @param supportedLocales 应用按优先级支持的 BCP 47 语言标签
     */
    public void setSupportedLocales(List<String> supportedLocales) {
        this.supportedLocales = new ArrayList<>(Objects.requireNonNull(supportedLocales, "支持的语言标签不能为空"));
    }

    /**
     * 请求语言均不受支持时使用的标签。
     *
     * @return 应用默认 BCP 47 语言标签
     */
    public String getDefaultLocale() {
        return defaultLocale;
    }

    /**
     * 替换请求语言均不受支持时使用的标签。
     *
     * @param defaultLocale 应用默认 BCP 47 语言标签
     */
    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }
}
