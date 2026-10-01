package com.sca4d.core.api.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.RealmKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 `ResourceReference` 的值相等规则不会破坏 Java equals 契约。 */
class ResourceReferenceTest {

    @Test
    @DisplayName("相同类型和标识的资源引用相等")
    void referencesWithTheSameTypeAndIdAreEqual() {
        ResourceReference first = new ResourceReference("FILE/RESOURCE", "12");
        ResourceReference second = new ResourceReference("FILE/RESOURCE", "12");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    @DisplayName("资源引用不与其他 Resource 实现跨类型相等")
    void doesNotCompareEqualToAnotherResourceImplementation() {
        Object reference = new ResourceReference("ACCOUNT/RESOURCE", "12");
        Object file = new Account(12L, 13L, new RealmKey("test"));

        assertNotEquals(reference, file);
        assertNotEquals(file, reference);
    }
}
