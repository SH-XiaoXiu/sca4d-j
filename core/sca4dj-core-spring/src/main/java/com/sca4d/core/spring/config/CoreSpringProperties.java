package com.sca4d.core.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Core Spring 的 HTTP 基础配置。 */
@ConfigurationProperties("sca4dj.core")
public class CoreSpringProperties {

    private final Pagination pagination = new Pagination();

    /** 单次列表请求允许的排序字段数量上限。 */
    private int sortMaxFields = 5;

    private final OpenApi openapi = new OpenApi();

    public CoreSpringProperties() {
    }

    /**
     * 默认每页数量。
     *
     * @return 分页查询未指定数量时使用的默认值
     */
    public int getPageDefaultSize() {
        return pagination.defaultSize;
    }

    /**
     * 设置默认每页数量。
     *
     * @param pageDefaultSize 正数默认分页数量
     */
    public void setPageDefaultSize(int pageDefaultSize) {
        pagination.setDefaultSize(pageDefaultSize);
    }

    /**
     * 单次分页查询允许的最大数量。
     *
     * @return 单次分页查询允许的最大数量
     */
    public int getPageMaxSize() {
        return pagination.maxSize;
    }

    /**
     * 设置单次分页查询允许的最大数量。
     *
     * @param pageMaxSize 不小于默认值的最大分页数量
     */
    public void setPageMaxSize(int pageMaxSize) {
        pagination.setMaxSize(pageMaxSize);
    }

    /**
     * 一个列表请求允许的最大排序字段数量。
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
        return openapi.enabled;
    }

    /**
     * 设置是否注册 OpenAPI 基础配置。
     *
     * @param openApiEnabled 是否注册 OpenAPI 基础配置
     */
    public void setOpenApiEnabled(boolean openApiEnabled) {
        openapi.setEnabled(openApiEnabled);
    }

    /** @return 分页资源配置 */
    public Pagination getPagination() {
        return pagination;
    }

    /** @return OpenAPI 基础资源配置 */
    public OpenApi getOpenapi() {
        return openapi;
    }

    /** 配置分页默认值和上限。 */
    public static class Pagination {

        private int defaultSize = 20;

        private int maxSize = 200;

        /** @return 未指定分页数量时使用的默认值 */
        public int getDefaultSize() {
            return defaultSize;
        }

        /** @param defaultSize 正数默认分页数量 */
        public void setDefaultSize(int defaultSize) {
            if (defaultSize < 1) {
                throw new IllegalArgumentException("默认分页数量必须为正数");
            }
            if (defaultSize > maxSize) {
                throw new IllegalArgumentException("默认分页数量不能大于最大分页数量");
            }
            this.defaultSize = defaultSize;
        }

        /** @return 单次分页允许的最大数量 */
        public int getMaxSize() {
            return maxSize;
        }

        /** @param maxSize 不小于默认分页数量的上限 */
        public void setMaxSize(int maxSize) {
            if (maxSize < defaultSize) {
                throw new IllegalArgumentException("最大分页数量不能小于默认分页数量");
            }
            this.maxSize = maxSize;
        }
    }

    /** 配置 Core OpenAPI 基础资源。 */
    public static class OpenApi {

        private boolean enabled = true;

        /** @return 是否注册 Core OpenAPI 基础配置 */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled 是否注册 Core OpenAPI 基础配置 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
