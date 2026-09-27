package com.sca4d.core.spring.setting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.codec.Codec;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.concurrent.OptimisticLockException;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BiFunction;

/** 把所属、版本及内容作为同一个文档原子保存；文件格式由传入 Codec 决定。 */
public final class FileSettingStore implements SettingReader<JsonNode>, SettingWriter<JsonNode> {
    private static final Logger LOG = LoggerFactory.getLogger(FileSettingStore.class);

    private final Path root;

    private final BiFunction<TreeCode, SettingOwner, Path> location;

    private final Codec<JsonNode, byte[]> format;

    private final DistributedLock lock;

    private final SecretKeyring keyring;

    /** 所有实例必须共享目录及锁；App 明确提供 owner 到文件位置的映射。 */
    public FileSettingStore(
            @NotNull Path root,
            @NotNull BiFunction<TreeCode, SettingOwner, Path> location,
            @NotNull Codec<JsonNode, byte[]> format,
            @NotNull DistributedLock lock,
            @Nullable SecretKeyring keyring) {
        this.root = root.toAbsolutePath().normalize();
        this.location = Objects.requireNonNull(location);
        this.format = Objects.requireNonNull(format);
        this.lock = Objects.requireNonNull(lock);
        this.keyring = keyring;
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.of("SETTING:FILE_STORE");
    }

    @Override
    public @NotNull SettingValue<JsonNode> read(@NotNull TreeCode setting, @NotNull SettingOwner owner) {
        Path path = path(setting, owner);
        try {
            if (!Files.exists(path))
                return SettingValue.read(code(), setting, owner, null, 0, new Version(0), false);
            JsonNode document = format.decode(FileSettingReader.readBytes(path));
            JsonNode metadata = document.path("_setting");
            if (!metadata.path("code").asText().equals(setting.format(NameFormat.COLON))
                    || !metadata.path("ownerType").asText().equals(owner.type().format(NameFormat.COLON))
                    || !metadata.path("ownerId").asText().equals(owner.id())) {
                throw new IllegalStateException("设置文件的编码或所属与访问目标不一致");
            }
            long revision = metadata.path("revision").asLong();
            int codecVersion = metadata.path("codecVersion").asInt();
            if (revision < 1 || codecVersion < 1 || !metadata.path("present").isBoolean()) {
                throw new IllegalStateException("设置文件版本元数据无效");
            }
            JsonNode value = null;
            if (metadata.path("present").booleanValue()) {
                value = document.get("value");
                if (metadata.path("secret").asBoolean()) {
                    if (keyring == null)
                        throw new IllegalStateException("敏感设置文件缺少密钥环");
                    String keyId = metadata.path("keyId").asText();
                    var cipher = keyring.findCipher(keyId).orElseThrow(() -> new IllegalStateException("设置文件密钥不存在"));
                    var codecs = new SettingSecretCodecCatalog(cipher, owner, setting.format(NameFormat.COLON));
                    value = format.decode(
                            codecs.select(metadata.path("encryptionContextVersion").asInt())
                                    .decode(new EncryptedSecret(keyId, document.path("ciphertext").binaryValue())));
                }
                if (value == null)
                    throw new IllegalStateException("设置文件缺少内容");
            }
            LOG.trace("设置文件已读取 | settingCode={} revision={}", setting, revision);
            return SettingValue.read(code(), setting, owner, value, codecVersion, new Version(revision), false);
        } catch (IOException failure) {
            throw new IllegalStateException("设置文件读取失败: " + setting, failure);
        }
    }

    @Override
    public void replace(
            @NotNull TreeCode setting,
            @NotNull SettingOwner owner,
            @NotNull JsonNode value,
            int codecVersion,
            boolean secret,
            @NotNull Version expected) {
        save(setting, owner, Objects.requireNonNull(value), codecVersion, secret, expected);
    }

    @Override
    public void reset(
            @NotNull TreeCode setting,
            @NotNull SettingOwner owner,
            int codecVersion,
            @NotNull Version expected) {
        save(setting, owner, null, codecVersion, false, expected);
    }

    @Override
    public void erase(@NotNull TreeCode setting, @NotNull SettingOwner owner) {
        lock.execute(
                lockKey(setting, owner),
                Duration.ofSeconds(10),
                () -> {
                    try {
                        Files.deleteIfExists(path(setting, owner));
                    } catch (IOException failure) {
                        throw new IllegalStateException("设置文件删除失败", failure);
                    }
                    return null;
                });
    }

    /** 在同一把跨实例锁下检查修订并原子发布，内容与版本不会分开提交。 */
    private void save(
            TreeCode setting,
            SettingOwner owner,
            JsonNode value,
            int codecVersion,
            boolean secret,
            Version expected) {
        if (codecVersion < 1)
            throw new IllegalArgumentException("写入 Codec 版本必须为正数");
        lock.execute(
                lockKey(setting, owner),
                Duration.ofSeconds(10),
                () -> {
                    if (!read(setting, owner).revision().orElseThrow().equals(expected))
                        throw new OptimisticLockException("设置文件 " + setting);
                    Path target = path(setting, owner);
                    Path temporary = null;
                    try {
                        ObjectNode document = JsonNodeFactory.instance.objectNode();
                        ObjectNode metadata = document.putObject("_setting");
                        metadata.put("code", setting.format(NameFormat.COLON));
                        metadata.put("ownerType", owner.type().format(NameFormat.COLON));
                        metadata.put("ownerId", owner.id());
                        metadata.put("codecVersion", codecVersion);
                        metadata.put("revision", Math.addExact(expected.value(), 1));
                        metadata.put("present", value != null);
                        metadata.put("secret", secret);
                        if (value != null && secret) {
                            if (keyring == null)
                                throw new IllegalStateException("敏感文件设置缺少 SecretKeyring");
                            var codecs = new SettingSecretCodecCatalog(
                                    keyring.activeCipher(),
                                    owner,
                                    setting.format(NameFormat.COLON));
                            var encrypted = codecs.current().encode(format.encode(value));
                            metadata.put("keyId", encrypted.keyId());
                            metadata.put("encryptionContextVersion", codecs.current().version());
                            document.put("ciphertext", encrypted.ciphertext());
                        } else if (value != null)
                            document.set("value", value.deepCopy());
                        byte[] encoded = format.encode(document);
                        if (encoded.length > 1_048_576)
                            throw new IllegalArgumentException("设置文件超过 1 MiB");
                        // 文件系统不支持原子替换时直接失败，不退回可能留下半份文件的覆盖写入。
                        Files.createDirectories(target.getParent());
                        FileSettingReader.checkedPath(root, target);
                        temporary = Files.createTempFile(target.getParent(), ".setting-", ".tmp");
                        Files.write(temporary, encoded);
                        Files.move(
                                temporary,
                                target,
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                        LOG.debug("设置文件已保存 | settingCode={} revision={}", setting, expected.value() + 1);
                        return null;
                    } catch (IOException failure) {
                        throw new IllegalStateException("设置文件原子保存失败", failure);
                    } finally {
                        if (temporary != null)
                            try {
                                Files.deleteIfExists(temporary);
                            } catch (IOException failure) {
                                LOG.warn("设置临时文件清理失败 | settingCode={}", setting, failure);
                            }
                    }
                });
    }

    /** 用实际文件位置协调写入，避免两个 owner 映射到同一文件时分别取得不同锁。 */
    private LockKey lockKey(TreeCode setting, SettingOwner owner) {
        return new LockKey(TreeCode.of("SETTING:FILE"), path(setting, owner).toString());
    }

    /** 解析并检查映射路径。 */
    private Path path(TreeCode setting, SettingOwner owner) {
        return FileSettingReader.checkedPath(root, location.apply(setting, owner));
    }
}
