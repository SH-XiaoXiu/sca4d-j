package com.sca4d.open.graphql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 配置平台开放 GraphQL 的开关、查询限制和 Schema introspection。 */
@ConfigurationProperties("sca4dj.open.graphql")
public class OpenGraphQlProperties {

    private boolean enabled = true;

    private boolean introspectionEnabled;

    private int maxDepth = 8;

    private int maxComplexity = 1000;

    /** @return 是否注册平台开放 GraphQL 组件 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否注册平台开放 GraphQL 组件 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 是否允许 introspection 查询 */
    public boolean isIntrospectionEnabled() {
        return introspectionEnabled;
    }

    /** @param introspectionEnabled 是否允许 introspection 查询 */
    public void setIntrospectionEnabled(boolean introspectionEnabled) {
        this.introspectionEnabled = introspectionEnabled;
    }

    /** @return GraphQL 查询最大嵌套深度 */
    public int getMaxDepth() {
        return maxDepth;
    }

    /** @param maxDepth 必须为正数的 GraphQL 查询最大嵌套深度 */
    public void setMaxDepth(int maxDepth) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("GraphQL 最大查询深度必须大于零");
        }
        this.maxDepth = maxDepth;
    }

    /** @return GraphQL 查询最大复杂度 */
    public int getMaxComplexity() {
        return maxComplexity;
    }

    /** @param maxComplexity 必须为正数的 GraphQL 查询最大复杂度 */
    public void setMaxComplexity(int maxComplexity) {
        if (maxComplexity <= 0) {
            throw new IllegalArgumentException("GraphQL 最大查询复杂度必须大于零");
        }
        this.maxComplexity = maxComplexity;
    }
}
