package com.sca4d.core.api;

import static org.junit.jupiter.api.Assertions.*;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.Test;

/** 验证稳定节点链独立于输入和输出格式，并保留完整层级语义。 */
class TreeCodeValueTest {

    @Test
    void threeFormatsRepresentTheSameCodeAndHash() {
        TreeCode dot = TreeCode.of("platform.users.profiles");
        TreeCode slash = TreeCode.of("PLATFORM/USERS/PROFILES");
        TreeCode colon = TreeCode.of("Platform:Users:Profiles");
        assertEquals(dot, slash);
        assertEquals(dot, colon);
        assertEquals(dot.hashCode(), colon.hashCode());
        assertEquals(0, dot.compareTo(slash));
        assertEquals("PLATFORM.USERS.PROFILES", dot.format(NameFormat.DOT));
        assertEquals("PLATFORM/USERS/PROFILES", dot.format(NameFormat.SLASH));
        assertEquals("PLATFORM:USERS:PROFILES", dot.format(NameFormat.COLON));
    }

    @Test
    void rejectsMixedFormatsEmptyNodesAndWhitespace() {
        assertThrows(IllegalArgumentException.class, () -> TreeCode.of("PLATFORM/USERS:PROFILES"));
        assertThrows(IllegalArgumentException.class, () -> TreeCode.of("PLATFORM.USERS/PROFILES"));
        assertThrows(IllegalArgumentException.class, () -> TreeCode.of("PLATFORM//PROFILES"));
        assertThrows(IllegalArgumentException.class, () -> TreeCode.of(" PLATFORM"));
        assertThrows(IllegalArgumentException.class, () -> TreeCode.of(""));
        assertEquals(TreeCode.of("PLATFORM"), TreeCode.ofSegment("platform"));
    }

    @Test
    void hierarchyQueriesAndAppendWorkWithoutAChosenFormat() {
        TreeCode code = TreeCode.of("PROJECTS.GROUPS.TESTS.READ");
        assertTrue(code.hasNode(TreeCode.of("groups/tests")));
        assertTrue(code.isUnder(TreeCode.of("PROJECTS:GROUPS")));
        assertFalse(code.isUnder(TreeCode.of("PROJECTS:GROUP")));
        assertFalse(code.hasNode(TreeCode.of("PROJECTS:READ")));
        assertEquals(TreeCode.of("ROOT:CHILD"), TreeCode.of("ROOT").child("CHILD"));
        assertEquals(TreeCode.of("ROOT:A:B"), TreeCode.of("ROOT").append(TreeCode.of("A/B")));
        assertThrows(IllegalArgumentException.class, () -> code.child(TreeCode.of("A:B")));
    }
}
