package com.sca4d.core.api.identity;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 表示 Subject 在某个认证域内使用的登录账号。
 */
public final class Account implements Resource {

    /**
     * Account 使用的稳定 Resource 类型。
     */
    public static final @NotNull TreeCode RESOURCE_TYPE = TreeCode.ofSlash("IAM/ACCOUNT");

    /**
     * 供 Java 注解参数使用的资源类型文本。
     */
    public static final @NotNull String RESOURCE_TYPE_VALUE = "IAM/ACCOUNT";

    private final long id;

    private final long subjectId;

    private final @NotNull RealmKey realmKey;

    /**
     * 创建认证域内的登录账号。
     *
     * @param id        Account 标识
     * @param subjectId 所属 Subject 标识
     * @param realmKey  创建时确定的认证域
     */
    public Account(
            long id,
            long subjectId,
            @NotNull RealmKey realmKey) {
        if (id <= 0 || subjectId <= 0) {
            throw new IllegalArgumentException("Account 及其 Subject 标识必须为正数");
        }
        this.id = id;
        this.subjectId = subjectId;
        this.realmKey = Objects.requireNonNull(realmKey, "Account 认证域不能为空");
    }

    /**
     * 读取登录账号的标识。
     *
     * @return Account 标识
     */
    @Contract(pure = true)
    public long accountId() {
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Contract(pure = true)
    public @NotNull TreeCode type() {
        return RESOURCE_TYPE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Contract(pure = true)
    public @NotNull String id() {
        return Long.toString(id);
    }

    /**
     * 读取拥有该登录账号的接入者标识。
     *
     * @return 所属 Subject 标识
     */
    @Contract(pure = true)
    public long subjectId() {
        return subjectId;
    }

    /**
     * 读取认证和账号查询必须使用的域。
     *
     * @return 账号创建后保持不变的认证域
     */
    @Contract(pure = true)
    public @NotNull RealmKey realmKey() {
        return realmKey;
    }
}
