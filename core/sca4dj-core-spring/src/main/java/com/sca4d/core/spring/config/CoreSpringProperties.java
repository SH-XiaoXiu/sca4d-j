package com.sca4d.core.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Core Spring 的 HTTP 基础配置。 */
@ConfigurationProperties("sca4dj.core")
public class CoreSpringProperties {

    private int pageDefaultSize = 20;

    private int pageMaxSize = 200;

    /** 单次列表请求允许的排序字段数量上限。 */
    private int sortMaxFields = 5;

    private boolean openApiEnabled = true;

    /** 创建使用默认分页和 OpenAPI 设置的配置。 */
    public CoreSpringProperties() {
    }

    /**
     * 返回默认每页数量。
     *
     * @return 分页查询未指定数量时使用的默认值
     */
    public int getPageDefaultSize() {
        return pageDefaultSize;
    }

    /**
     * 设置默认每页数量。
     *
     * @param pageDefaultSize 正数默认分页数量
     */
    public void setPageDefaultSize(int pageDefaultSize) {
        if (pageDefaultSize < 1) {
            throw new IllegalArgumentException("默认分页数量必须为正数");
        }
        this.pageDefaultSize = pageDefaultSize;
    }

    /**
     * 返回单次分页查询允许的最大数量。
     *
     * @return 单次分页查询允许的最大数量
     */
    public int getPageMaxSize() {
        return pageMaxSize;
    }

    /**
     * 设置单次分页查询允许的最大数量。
     *
     * @param pageMaxSize 不小于默认值的最大分页数量
     */
    public void setPageMaxSize(int pageMaxSize) {
        if (pageMaxSize < pageDefaultSize) {
            throw new IllegalArgumentException("最大分页数量不能小于默认分页数量");
        }
        this.pageMaxSize = pageMaxSize;
    }

    /**
     * 返回一个列表请求允许的最大排序字段数量。
     *
     * @return 1 到 20 的排序字段数量上限
     */
    public int getSortMaxFields() {
        return sortMaxFields;
    }

    /**
     * 设置一个列表请求允许的最大排序字段数量。
     *
     * @param sortMaxFields 1 到 20 的排序字段数量上限
     */
    public void setSortMaxFields(int sortMaxFields) {
        if (sortMaxFields < 1 || sortMaxFields > 20) {
            throw new IllegalArgumentException("最大排序字段数量必须为 1 到 20");
        }
        this.sortMaxFields = sortMaxFields;
    }

    /**
     * 判断是否注册 OpenAPI 基础配置。
     *
     * @return 注册 OpenAPI 基础配置时返回 {@code true}
     */
    public boolean isOpenApiEnabled() {
        return openApiEnabled;
    }

    /**
     * 设置是否注册 OpenAPI 基础配置。
     *
     * @param openApiEnabled 是否注册 OpenAPI 基础配置
     */
    public void setOpenApiEnabled(boolean openApiEnabled) {
        this.openApiEnabled = openApiEnabled;
    }
}
