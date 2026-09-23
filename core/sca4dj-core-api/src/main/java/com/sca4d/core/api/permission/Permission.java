package com.sca4d.core.api.permission;

import com.sca4d.core.api.resource.Resource;

import java.util.Objects;

/**
 * 表示对一个资源执行一个动作的完整权限（Permission）。
 *
 * <p>权限严格由资源（Resource，{@link Resource}）和动作（Action，{@link Action}）组成，不额外维护接口权限键。
 */
public final class Permission {

    private final Resource resource;

    private final Action action;

    /**
     * 创建权限。
     *
     * @param resource 受保护资源
     * @param action   对资源执行的动作
     */
    public Permission(Resource resource, Action action) {
        this.resource = Objects.requireNonNull(resource, "权限资源不能为空");
        this.action = Objects.requireNonNull(action, "权限动作不能为空");
        Objects.requireNonNull(resource.type(), "资源类型不能为空");
        requireText(resource.id(), "资源标识");
    }

    /**
     * 返回受保护资源。
     *
     * @return 受保护资源
     */
    public Resource resource() {
        return resource;
    }

    /**
     * 返回对资源执行的动作。
     *
     * @return 对资源执行的动作
     */
    public Action action() {
        return action;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof Permission permission
                        && resource.type().equals(permission.resource.type())
                        && resource.id().equals(permission.resource.id())
                        && action.equals(permission.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource.type(), resource.id(), action);
    }

    @Override
    public String toString() {
        return resource.type() + ":" + resource.id() + ":" + action.code();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空白");
        }
    }
}
