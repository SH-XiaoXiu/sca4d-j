package com.sca4d.iot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.code.TreeCode;
import org.junit.jupiter.api.Test;

class DeviceAccessMethodTest {

    @Test
    void preservesFullAuthenticationMethodPath() {
        DeviceAccessMethod parsed = DeviceAccessMethod.parse("ACCOUNT:AUTHENTICATION/DEVICE");

        assertEquals("ACCOUNT:AUTHENTICATION/DEVICE", parsed.code());
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
        assertEquals("ANONYMOUS", DeviceAccessMethod.anonymous().code());
        assertEquals("SESSION", DeviceAccessMethod.session().code());
    }
}
