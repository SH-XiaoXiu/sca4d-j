package com.sca4d.core.spring.persistence.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置单条 SQL 批量操作的条目数和绑定参数保护上限。 */
@ConfigurationProperties("sca4dj.persistence.batch")
public class SqlBatchProperties {

    private int maxItems = 1_000;

    private int maxBindParameters = 30_000;

    public SqlBatchProperties() {
    }

    /** @return 单次 SQL 最多包含的集合条目数 */
    public int getMaxItems() {
        return maxItems;
    }

    /** @param maxItems 单次 SQL 最多包含的正数集合条目数 */
    public void setMaxItems(int maxItems) {
        if (maxItems < 1) {
            throw new IllegalArgumentException("SQL 批次条目上限必须为正数");
        }
        this.maxItems = maxItems;
    }

    /** @return 单次 SQL 最多使用的绑定参数数量 */
    public int getMaxBindParameters() {
        return maxBindParameters;
    }

    /** @param maxBindParameters 1 到 65535 的 SQL 绑定参数保护上限 */
    public void setMaxBindParameters(int maxBindParameters) {
        if (maxBindParameters < 1 || maxBindParameters > 65_535) {
            throw new IllegalArgumentException("SQL 绑定参数上限必须为 1 到 65535");
        }
        this.maxBindParameters = maxBindParameters;
    }
}
