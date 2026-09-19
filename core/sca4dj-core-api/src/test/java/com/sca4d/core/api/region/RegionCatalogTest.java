package com.sca4d.core.api.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionCatalogTest {

    private final RegionCatalog catalog = RegionCatalog.standard();

    @Test
    @DisplayName("Covers Every Libphonenumber Geographical Region")
    void coversEveryLibphonenumberGeographicalRegion() {
        assertTrue(
                PhoneNumberUtil.getInstance()
                        .getSupportedRegions()
                        .stream()
                        .allMatch(region -> catalog.continentOf(region) != null));
    }

    @Test
    @DisplayName("Resolves Representative Regions And Calling Codes")
    void resolvesRepresentativeRegionsAndCallingCodes() {
        assertEquals(Continent.ASIA, catalog.continentOf("cn"));
        assertEquals(Continent.NORTH_AMERICA, catalog.continentOf("US"));
        assertEquals(Continent.EUROPE, catalog.continentOf("GB"));
        assertEquals(86, catalog.countryCallingCode("CN"));
        assertEquals(1, catalog.countryCallingCode("US"));
    }

    @Test
    @DisplayName("Non Geographical Code Has No Continent")
    void nonGeographicalCodeHasNoContinent() {
        assertNull(catalog.continentOf("001"));
    }

    @Test
    @DisplayName("Parses Canonical Number Into Core Types")
    void parsesCanonicalNumberIntoCoreTypes() {
        ParsedPhoneNumber phoneNumber = catalog.parse("+8613800138000");

        assertNotNull(phoneNumber);
        assertEquals("+8613800138000", phoneNumber.value());
        assertEquals(new Region("CN"), phoneNumber.region());
        assertEquals(Continent.ASIA, phoneNumber.continent());
        assertEquals(86, phoneNumber.countryCallingCode());
        assertEquals(PhoneNumberType.MOBILE, phoneNumber.type());
        assertNull(catalog.parse("+86 138 0013 8000"));
    }
}
