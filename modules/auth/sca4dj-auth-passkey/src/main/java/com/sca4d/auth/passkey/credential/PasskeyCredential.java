package com.sca4d.auth.passkey.credential;

import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 保存 Passkey 模块验证 WebAuthn assertion 所需的公开密钥和计数器状态。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class PasskeyCredential {

    private final long id;

    private final long accountId;

    private final byte[] userHandle;

    private final byte[] credentialId;

    private final byte[] publicKeyCose;

    private final long signatureCount;

    private final Set<String> transports;

    private final boolean backupEligible;

    private final boolean backedUp;

    private final String name;

    private final Instant createdAt;

    private final @Nullable Instant lastUsedAt;

    private final Version version;

    /**
     * 创建数据库已保存的 Passkey Credential。
     *
     * @throws IllegalArgumentException 标识、计数器或二进制字段无效时抛出
     */
    public PasskeyCredential(
            long id,
            long accountId,
            byte[] userHandle,
            byte[] credentialId,
            byte[] publicKeyCose,
            long signatureCount,
            Set<String> transports,
            boolean backupEligible,
            boolean backedUp,
            String name,
            Instant createdAt,
            @Nullable Instant lastUsedAt,
            Version version) {
        if (id < 0 || accountId <= 0 || signatureCount < 0) {
            throw new IllegalArgumentException("Passkey 标识和签名计数器无效");
        }
        this.id = id;
        this.accountId = accountId;
        this.userHandle = requireBytes(userHandle, "Passkey userHandle");
        this.credentialId = requireBytes(credentialId, "Passkey credentialId");
        this.publicKeyCose = requireBytes(publicKeyCose, "Passkey COSE 公钥");
        this.signatureCount = signatureCount;
        this.transports = Set.copyOf(Objects.requireNonNull(transports, "Passkey transports 不能为空"));
        this.backupEligible = backupEligible;
        this.backedUp = backedUp;
        this.name = requireName(name);
        this.createdAt = Objects.requireNonNull(createdAt, "Passkey 创建时间不能为空");
        this.lastUsedAt = lastUsedAt;
        this.version = Objects.requireNonNull(version, "Passkey 版本不能为空");
    }

    /**
     * 返回模块内部 Credential 标识；registration 校验后尚未写入数据库时为 0。
     */
    public long id() {
        return id;
    }

    /**
     * 返回 Credential 所属账号。
     */
    public long accountId() {
        return accountId;
    }

    /**
     * 返回 WebAuthn user handle 的副本。
     */
    public byte[] userHandle() {
        return userHandle.clone();
    }

    /**
     * 返回 WebAuthn credential ID 的副本。
     */
    public byte[] credentialId() {
        return credentialId.clone();
    }

    /**
     * 返回 COSE 编码公钥的副本。
     */
    public byte[] publicKeyCose() {
        return publicKeyCose.clone();
    }

    /**
     * 返回最近一次成功 assertion 保存的签名计数器。
     */
    public long signatureCount() {
        return signatureCount;
    }

    /**
     * 返回浏览器报告的不可修改 authenticator transport 集合。
     */
    public @NotNull @Unmodifiable Set<String> transports() {
        return transports;
    }

    /**
     * 返回 Credential 是否允许同步备份。
     */
    public boolean backupEligible() {
        return backupEligible;
    }

    /**
     * 返回 Credential 最近一次 assertion 是否来自已备份状态。
     */
    public boolean backedUp() {
        return backedUp;
    }

    /**
     * 返回用户为 Credential 设置的名称。
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * 返回 Credential 创建时间。
     */
    public @NotNull Instant createdAt() {
        return createdAt;
    }

    /**
     * 返回 Credential 最近一次成功使用时间；从未使用时为 {@code null}。
     */
    public @Nullable Instant lastUsedAt() {
        return lastUsedAt;
    }

    /**
     * 返回 Credential 当前乐观版本。
     */
    public @NotNull Version version() {
        return version;
    }

    /**
     * 复制并校验必须包含内容的二进制字段。
     */
    private static byte[] requireBytes(byte[] value, String name) {
        byte[] copied = Objects.requireNonNull(value, name + "不能为空").clone();
        if (copied.length == 0) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return copied;
    }

    /**
     * 校验并规范化用户设置的 Passkey 名称。
     */
    private static String requireName(String value) {
        String checked = Objects.requireNonNull(value, "Passkey 名称不能为空").trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("Passkey 名称不能为空");
        }
        return checked;
    }
}
