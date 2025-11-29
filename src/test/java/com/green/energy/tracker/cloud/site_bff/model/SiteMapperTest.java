package com.green.energy.tracker.cloud.site_bff.model;

import com.green.energy.tracker.cloud.sitebff.web.model.SiteResponseDto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SiteMapperTest {

    private final SiteMapper siteMapper = Mappers.getMapper(SiteMapper.class);

    @Test
    void toDto_shouldMapAllFields() {
        // Arrange
        var now = OffsetDateTime.now();
        var siteId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var location = GeoLocationRead.builder()
                .latitude(40.7128)
                .longitude(-74.0060)
                .build();

        var document = SiteReadDocument.builder()
                .id(siteId.toString())
                .name("Test Site")
                .userId(userId.toString())
                .address("123 Main Street")
                .location(location)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Act
        SiteResponseDto result = siteMapper.toDto(document);

        // Assert
        assertNotNull(result);
        assertEquals(siteId, result.getId());
        assertEquals("Test Site", result.getName());
        assertEquals(userId, result.getUserId());
        assertEquals("123 Main Street", result.getAddress());
        assertNotNull(result.getLocation());
        assertEquals(40.7128, result.getLocation().getLatitude());
        assertEquals(-74.0060, result.getLocation().getLongitude());
        assertEquals(now, result.getCreatedAt());
        assertEquals(now, result.getUpdatedAt());
    }

    @Test
    void toDto_withNullLocation_shouldHandleGracefully() {
        // Arrange
        var now = OffsetDateTime.now();
        var siteId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var document = SiteReadDocument.builder()
                .id(siteId.toString())
                .name("Test Site")
                .userId(userId.toString())
                .address("123 Main Street")
                .location(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Act
        SiteResponseDto result = siteMapper.toDto(document);

        // Assert
        assertNotNull(result);
        assertNull(result.getLocation());
    }
}
