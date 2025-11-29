package com.green.energy.tracker.cloud.site_bff.model;

import com.google.cloud.Timestamp;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteResponseDto;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface SiteMapper {
    SiteResponseDto toDto(SiteReadDocument entity);

    default OffsetDateTime timestampToOffsetDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    default Timestamp offsetDateTimeToTimestamp(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        Instant instant = offsetDateTime.toInstant();
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    default UUID stringToUuid(String value) {
        if (value == null) {
            return null;
        }
        return UUID.fromString(value);
    }

    default String uuidToString(UUID value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
