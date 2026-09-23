package com.sca4d.core.spring.setting;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.setting.SettingOwner;
import org.junit.jupiter.api.Test;

/** 验证数据库中的单段系统 owner 与层级资源 owner 均可读取。 */
class StoredSettingTest {

    @Test
    void readsSystemOwnerFromStoredSetting() {
        StoredSetting stored = stored("SYSTEM", "1");

        assertThat(stored.owner()).isEqualTo(SettingOwner.system());
    }

    @Test
    void readsHierarchicalResourceOwnerFromStoredSetting() {
        StoredSetting stored = stored("IAM:ACCOUNT", "42");

        assertThat(stored.owner().type().value()).isEqualTo("IAM:ACCOUNT");
        assertThat(stored.owner().id()).isEqualTo("42");
    }

    private static StoredSetting stored(String ownerType, String ownerId) {
        return new StoredSetting(
                ownerType,
                ownerId,
                "brand.product-name",
                1,
                true,
                "Product",
                null,
                null,
                null,
                false,
                1);
    }
}
