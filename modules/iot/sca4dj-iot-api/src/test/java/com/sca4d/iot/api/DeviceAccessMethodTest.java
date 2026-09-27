package com.sca4d.iot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.Test;

class DeviceAccessMethodTest {

    @Test
    void preservesFullAuthenticationMethodPath() {
        DeviceAccessMethod parsed = DeviceAccessMethod.parse("ACCOUNT:AUTHENTICATION/DEVICE");

        assertEquals(TreeCode.ofColon("ACCOUNT:AUTHENTICATION:DEVICE"), parsed.code());
        assertEquals(
                DeviceAccessMethod.authentication(TreeCode.ofSlash("AUTHENTICATION/DEVICE")),
                parsed);
    }

    @Test
    void rejectsShortAuthenticationMethodCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DeviceAccessMethod.parse("ACCOUNT:SHARED_SECRET"));
    }

    @Test
    void usesUppercaseReservedAccessMethodCodes() {
        assertEquals(TreeCode.ofSegment("ANONYMOUS"), DeviceAccessMethod.anonymous().code());
        assertEquals(TreeCode.ofSegment("SESSION"), DeviceAccessMethod.session().code());
    }
}
