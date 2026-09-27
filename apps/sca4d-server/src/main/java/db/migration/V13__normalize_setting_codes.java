package db.migration;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.api.security.SecretCipher;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.spring.setting.SettingSecretCodecAdapter;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 由 Flyway 将旧设置键转换为严格 TreeCode，并重绑敏感值的加密用途。 */
public final class V13__normalize_setting_codes extends BaseJavaMigration {
    private final SecretKeyring testKeyring;

    public V13__normalize_setting_codes() {
        this(null);
    }

    public V13__normalize_setting_codes(SecretKeyring testKeyring) {
        this.testKeyring = testKeyring;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Map<SettingOwner, Set<TreeCode>> seen = new HashMap<>();
        try (var query = connection.prepareStatement(
                "SELECT owner_type, owner_id, setting_key FROM sys_setting ORDER BY owner_type, owner_id, setting_key FOR UPDATE");
                var rows = query.executeQuery()) {
            while (rows.next()) {
                SettingOwner owner = new SettingOwner(
                        TreeCode.of(rows.getString("owner_type")),
                        rows.getString("owner_id"));
                TreeCode code = canonical(rows.getString("setting_key"));
                if (code.hasNode(TreeCode.of("*"))
                        || !seen.computeIfAbsent(owner, ignored -> new HashSet<>()).add(code)) {
                    throw new IllegalStateException("设置编码归一存在冲突，迁移已中止");
                }
            }
        }
        try (var query = connection.prepareStatement(
                "SELECT owner_type, owner_id, setting_key, value_key_id, value_ciphertext, encryption_context_version FROM sys_setting ORDER BY owner_type, owner_id, setting_key");
                var rows = query.executeQuery();
                var update = connection.prepareStatement(
                        "UPDATE sys_setting SET setting_key = ?, value_key_id = ?, value_ciphertext = ?, encryption_context_version = ?, version = version + 1 WHERE owner_type = ? AND owner_id = ? AND setting_key = ?")) {
            while (rows.next()) {
                String old = rows.getString("setting_key");
                String code = canonical(old).format(NameFormat.COLON);
                if (old.equals(code))
                    continue;
                String ownerType = rows.getString("owner_type");
                String ownerId = rows.getString("owner_id");
                String keyId = rows.getString("value_key_id");
                byte[] ciphertext = rows.getBytes("value_ciphertext");
                Integer encryptionVersion = (Integer) rows.getObject("encryption_context_version");
                if (ciphertext != null) {
                    SecretKeyring keyring = testKeyring == null ? configuredKeyring() : testKeyring;
                    if (keyring == null)
                        throw new IllegalStateException("迁移敏感设置需要密钥环环境变量");
                    SettingOwner owner = new SettingOwner(TreeCode.of(ownerType), ownerId);
                    String sourceKeyId = keyId;
                    SecretCipher cipher = keyring.findCipher(sourceKeyId)
                            .orElseThrow(() -> new IllegalStateException("迁移敏感设置缺少历史密钥: " + sourceKeyId));
                    EncryptedSecret rebound = SettingSecretCodecAdapter.rebind(
                            cipher,
                            owner,
                            code,
                            old,
                            encryptionVersion,
                            new EncryptedSecret(keyId, ciphertext));
                    keyId = rebound.keyId();
                    ciphertext = rebound.ciphertext();
                }
                update.setString(1, code);
                update.setString(2, keyId);
                update.setBytes(3, ciphertext);
                update.setObject(4, encryptionVersion);
                update.setString(5, ownerType);
                update.setString(6, ownerId);
                update.setString(7, old);
                if (update.executeUpdate() != 1)
                    throw new IllegalStateException("设置编码迁移未更新预期记录");
            }
        }
    }

    /** 旧数据允许点、斜杠和连字符，迁移输出只使用严格 TreeCode 节点。 */
    private static TreeCode canonical(String value) {
        return TreeCode.of(value.replace('-', '_').replace('/', ':').replace('.', ':'));
    }

    /** Flyway 运行在 Spring 之前，从部署环境恢复应用相同的密钥材料；不生成新 key。 */
    private static SecretKeyring configuredKeyring() {
        String active = System.getenv("SCA4DJ_SECRET_KEYRING_ACTIVE_KEY_ID");
        if (active == null || active.isBlank())
            return null;
        Map<String, SecretCipher> ciphers = new HashMap<>();
        System.getenv().forEach((name, value) -> {
            String prefix = "SCA4DJ_SECRET_KEYRING_";
            if (name.startsWith(prefix) && name.endsWith("_KEY") && !name.equals(prefix + "ACTIVE_KEY_ID")) {
                String keyId = name.substring(prefix.length(), name.length() - 4).toLowerCase().replace('_', '-');
                ciphers.put(
                        keyId,
                        new com.sca4d.core.spring.security.AesGcmSecretCipher(
                                keyId,
                                java.util.Base64.getDecoder().decode(value)));
            }
        });
        SecretCipher activeCipher = ciphers.get(active);
        if (activeCipher == null)
            throw new IllegalStateException("密钥环缺少 active key: " + active);
        return new SecretKeyring() {
            @Override
            public SecretCipher activeCipher() {
                return activeCipher;
            }

            @Override
            public java.util.Optional<SecretCipher> findCipher(String keyId) {
                return java.util.Optional.ofNullable(ciphers.get(keyId));
            }
        };
    }

}
