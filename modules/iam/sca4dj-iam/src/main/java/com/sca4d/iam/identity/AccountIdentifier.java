package com.sca4d.iam.identity;

import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.identity.RealmKey;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存账号的一种登录标识及其稳定查询值。 */
@SuppressWarnings("ClassCanBeRecord")
public class AccountIdentifier {

    private final long id;

    private final @NotNull AccountIdentifierType type;

    private final @NotNull IdentityStatus status;

    private final @NotNull Long accountId;

    private final @NotNull RealmKey realmKey;

    private final @NotNull String normalizedValue;

    private final @NotNull String value;

    /**
     * 创建账号登录标识。
     *
     * @param id 登录标识主键
     * @param accountId 所属账号
     * @param realmKey 认证域
     * @param type 标识类型
     * @param value 展示原值
     * @param normalizedValue 查询规范值
     * @param status 当前状态
     */
    public AccountIdentifier(
            @NotNull Long id,
            @NotNull Long accountId,
            @NotNull RealmKey realmKey,
            @NotNull AccountIdentifierType type,
            @NotNull String value,
            @NotNull String normalizedValue,
            @NotNull IdentityStatus status) {
        long checkedId = Objects.requireNonNull(id, "登录标识主键不能为空");
        if (checkedId <= 0) {
            throw new IllegalArgumentException("登录标识主键必须为正数");
        }
        this.id = checkedId;
        this.accountId = Objects.requireNonNull(accountId, "登录标识所属账号不能为空");
        this.realmKey = Objects.requireNonNull(realmKey, "登录标识认证域不能为空");
        this.type = Objects.requireNonNull(type, "登录标识类型不能为空");
        this.value = Objects.requireNonNull(value, "登录标识原值不能为空");
        this.normalizedValue = Objects.requireNonNull(normalizedValue, "登录标识规范值不能为空");
        this.status = Objects.requireNonNull(status, "登录标识状态不能为空");
    }

    /**
     * 登录标识主键。
     *
     * @return 登录标识主键
     */
    @Contract(pure = true)
    public long id() {
        return id;
    }

    /**
     * 登录标识所属账号。
     *
     * @return 所属账号
     */
    @Contract(pure = true)
    public @NotNull Long accountId() {
        return accountId;
    }

    /**
     * 登录标识所属认证域。
     *
     * @return 认证域
     */
    @Contract(pure = true)
    public @NotNull RealmKey realmKey() {
        return realmKey;
    }

    /**
     * 登录标识类型。
     *
     * @return 标识类型
     */
    @Contract(pure = true)
    public @NotNull AccountIdentifierType type() {
        return type;
    }

    /**
     * 保留用于展示的用户输入值。
     *
     * @return 展示原值
     */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    /**
     * 查找账号和唯一约束使用的规范值。
     *
     * @return 查询规范值
     */
    @Contract(pure = true)
    public @NotNull String normalizedValue() {
        return normalizedValue;
    }

    /**
     * 登录标识当前是否可用。
     *
     * @return 当前状态
     */
    @Contract(pure = true)
    public @NotNull IdentityStatus status() {
        return status;
    }

}
