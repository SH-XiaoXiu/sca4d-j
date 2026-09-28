package com.sca4d.core.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置外部 HTTP 输入按 Java 数据类型自动执行的默认校验。 */
@ConfigurationProperties("sca4dj.core.http.input.validation")
public class HttpInputValidationProperties {

    private boolean positiveIntegerEnabled = true;

    private boolean requestBodyValidationEnabled = true;

    private boolean stringSizeEnabled = true;

    private int maxStringLength = 4096;

    private boolean containerSizeEnabled = true;

    private int maxContainerSize = 1000;

    /** 创建启用全部 HTTP 输入默认校验的配置。 */
    public HttpInputValidationProperties() {
    }

    /** @return 外部整数没有显式范围约束时是否要求大于零 */
    public boolean isPositiveIntegerEnabled() {
        return positiveIntegerEnabled;
    }

    /** @param positiveIntegerEnabled 是否要求无显式范围约束的外部整数大于零 */
    public void setPositiveIntegerEnabled(boolean positiveIntegerEnabled) {
        this.positiveIntegerEnabled = positiveIntegerEnabled;
    }

    /** @return 是否默认校验每个 {@code @RequestBody} 对象上的 Bean Validation 约束 */
    public boolean isRequestBodyValidationEnabled() {
        return requestBodyValidationEnabled;
    }

    /** @param requestBodyValidationEnabled 是否默认校验请求体 Bean Validation 约束 */
    public void setRequestBodyValidationEnabled(boolean requestBodyValidationEnabled) {
        this.requestBodyValidationEnabled = requestBodyValidationEnabled;
    }

    /** @return 外部字符串没有显式 {@code @Size} 时是否限制最大长度 */
    public boolean isStringSizeEnabled() {
        return stringSizeEnabled;
    }

    /** @param stringSizeEnabled 是否限制无显式 {@code @Size} 的外部字符串 */
    public void setStringSizeEnabled(boolean stringSizeEnabled) {
        this.stringSizeEnabled = stringSizeEnabled;
    }

    /** @return 无显式 {@code @Size} 的外部字符串允许包含的最多字符数 */
    public int getMaxStringLength() {
        return maxStringLength;
    }

    /**
     * @param maxStringLength 无显式 {@code @Size} 的外部字符串最大字符数
     * @throws IllegalArgumentException 最大字符数小于一时抛出
     */
    public void setMaxStringLength(int maxStringLength) {
        if (maxStringLength < 1) {
            throw new IllegalArgumentException("HTTP 输入字符串最大长度必须为正数");
        }
        this.maxStringLength = maxStringLength;
    }

    /** @return 外部容器没有显式 {@code @Size} 时是否限制最大元素数 */
    public boolean isContainerSizeEnabled() {
        return containerSizeEnabled;
    }

    /** @param containerSizeEnabled 是否限制无显式 {@code @Size} 的外部容器 */
    public void setContainerSizeEnabled(boolean containerSizeEnabled) {
        this.containerSizeEnabled = containerSizeEnabled;
    }

    /** @return 无显式 {@code @Size} 的外部容器允许包含的最多元素数 */
    public int getMaxContainerSize() {
        return maxContainerSize;
    }

    /**
     * @param maxContainerSize 无显式 {@code @Size} 的外部容器最大元素数
     * @throws IllegalArgumentException 最大元素数小于一时抛出
     */
    public void setMaxContainerSize(int maxContainerSize) {
        if (maxContainerSize < 1) {
            throw new IllegalArgumentException("HTTP 输入容器最大元素数必须为正数");
        }
        this.maxContainerSize = maxContainerSize;
    }
}
