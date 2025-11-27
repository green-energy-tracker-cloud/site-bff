package com.green.energy.tracker.cloud.site_bff.model;

import com.green.energy.tracker.cloud.sitebff.web.model.SiteResponseDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SiteMapper {
    SiteResponseDto toDto(SiteReadDocument entity);
}
