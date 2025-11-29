package com.green.energy.tracker.cloud.site_bff.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GeoLocationReadTest {

    @Test
    void testLombokMethods() {
        // Arrange
        var location1 = new GeoLocationRead(10.0, 20.0);
        var location2 = GeoLocationRead.builder()
                .latitude(10.0)
                .longitude(20.0)
                .build();

        // Assert
        assertEquals(location1, location2);
        assertEquals(location1.hashCode(), location2.hashCode());
        assertNotNull(location1.toString());

        assertEquals(10.0, location1.getLatitude());
        assertEquals(20.0, location1.getLongitude());
    }

    @Test
    void testNoArgsConstructor() {
        GeoLocationRead location = new GeoLocationRead();
        assertEquals(0.0, location.getLatitude());
        assertEquals(0.0, location.getLongitude());
    }
}
