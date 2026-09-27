package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.*;

import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.scheduling.ScheduleKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证名称值、领域编码、动态键及 App 隔离的身份与编码规则。 */
class NameTest {

    /** 为当前测试进程固定一个 App 名，与单 JVM 一个全局 App 的约定一致。 */
    @BeforeAll
    static void initializeAppName() {
        AppName.publish("sca4d-test");
    }

    @Test
    void ordinaryNamePreservesItsStringValueAndCase() {
        Name name = new Name("a.b.c");
        assertEquals("a.b.c", name.format(NameFormat.COLON));
        assertNotEquals(name, new Name("A.B.C"));
    }

    @Test
    void domainTypesKeepTheirIdentityAndCovariantOperations() {
        JobKey job = new JobKey("task.cleanup");
        ScheduleKey schedule = new ScheduleKey("TASK/CLEANUP");
        assertNotEquals(job, schedule);
        assertTrue(job.sameCode(schedule));
        JobKey child = job.child("HISTORY");
        assertEquals(new JobKey("TASK:CLEANUP:HISTORY"), child);
        assertNotEquals(0, job.compareTo(schedule));
    }

    @Test
    void dynamicKeysPreserveCaseDelimitersAndPartBoundaries() {
        TreeCode root = TreeCode.of("AUTH.SESSION");
        assertNotEquals(Key.of(root, "abc"), Key.of(root, "ABC"));
        assertNotEquals(Key.of(root, "a:b"), Key.of(root, "a/b"));
        assertNotEquals(Key.of(root, "a:b"), Key.of(root, "a", "b"));
        assertNotEquals(Key.of(root, ""), Key.of(root));
    }

    @Test
    void stableNodesCannotCollideWithEncodedDynamicValues() {
        TreeCode root = TreeCode.of("AUTH.SESSION");
        assertNotEquals(Key.of(root.child("V_6162")), Key.of(root, "ab"));
        assertNotEquals(Key.of(root.child("4")), Key.of(root, 4L));
        assertNotEquals(Key.of(root.child("C_4")), Key.of(root.child("4")));
    }

    @Test
    void keyOutputAddsTheAppPrefixAndRoundTrips() {
        Key key = Key.of(TreeCode.of("AUTH.SESSION"), 4L);
        assertEquals("SCA4D_TEST:AUTH:SESSION:4", key.format(NameFormat.COLON));
        assertEquals(key, Key.parse(key.format(NameFormat.COLON), NameFormat.COLON));
        Key text = Key.of(TreeCode.of("AUTH.SESSION"), "中文:A.b");
        assertEquals(text, Key.parse(text.format(NameFormat.COLON), NameFormat.COLON));
        assertThrows(IllegalArgumentException.class, () -> Key.parse("OTHER:AUTH:SESSION:4", NameFormat.COLON));
    }

    @Test
    void explicitIsolationDoesNotMutateCodeOrAddThePrefixTwice() {
        TreeCode code = TreeCode.of("AUTH.SESSION");
        Name isolated = code.isolate();
        assertEquals("AUTH:SESSION", code.format(NameFormat.COLON));
        assertEquals("SCA4D_TEST:AUTH:SESSION", isolated.format(NameFormat.COLON));
        assertSame(isolated, isolated.isolate());
        assertEquals("SCA4D_TEST:AUTH:SESSION", Key.of(code).isolate().format(NameFormat.COLON));
    }

    @Test
    void appNameNormalizationAndPublicationAreStrict() {
        assertEquals(new Name("SCA4D_TEST"), AppName.normalize("sca4d-test"));
        AppName.publish("SCA4D_TEST");
        assertThrows(IllegalStateException.class, () -> AppName.publish("other-app"));
        assertThrows(IllegalArgumentException.class, () -> AppName.normalize(" "));
        assertThrows(IllegalArgumentException.class, () -> AppName.normalize("app:name"));
    }

    @Test
    void malformedAndWildcardKeysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Key.of(TreeCode.of("AUTH:*")));
        assertThrows(IllegalArgumentException.class, () -> Key.fromSegments(List.of("V_FF")));
        assertThrows(IllegalArgumentException.class, () -> Key.fromSegments(List.of("V_6a")));
        assertThrows(IllegalArgumentException.class, () -> Key.fromSegments(List.of("04")));
    }
}
