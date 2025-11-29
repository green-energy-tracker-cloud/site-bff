package com.green.energy.tracker.cloud.site_bff.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SiteReadDocumentTest {

    @Test
    void testLombokMethods() {
        // Arrange
        var now = OffsetDateTime.now();
        var location = new GeoLocationRead(10.0, 20.0);

        // Act
        var doc1 = new SiteReadDocument("1", "Site 1", "user1", "Address 1", location, now, now);
        var doc2 = SiteReadDocument.builder()
                .id("1")
                .name("Site 1")
                .userId("user1")
                .address("Address 1")
                .location(location)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Assert
        assertEquals(doc1, doc2);
        assertEquals(doc1.hashCode(), doc2.hashCode());
        assertNotNull(doc1.toString());

        assertEquals("1", doc1.getId());
        assertEquals("Site 1", doc1.getName());
        assertEquals("user1", doc1.getUserId());
        assertEquals("Address 1", doc1.getAddress());
        assertEquals(location, doc1.getLocation());
        assertEquals(now, doc1.getCreatedAt());
        assertEquals(now, doc1.getUpdatedAt());
    }

    @Test
    void testNoArgsConstructor() {
        SiteReadDocument doc = new SiteReadDocument();
        assertNull(doc.getId());
    }
}
