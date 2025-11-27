package com.green.energy.tracker.cloud.site_bff.service.v1;

import com.green.energy.tracker.cloud.sitebff.web.model.AsyncOperationResponseDto;
import com.green.energy.tracker.cloud.sitebff.web.model.ListSitesResponseDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteRequestDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteResponseDto;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SiteService {
    Mono<AsyncOperationResponseDto> create(SiteRequestDto siteRequestDto);
    Mono<AsyncOperationResponseDto> delete(UUID id);
    Mono<SiteResponseDto> get(UUID id);
    Mono<ListSitesResponseDto> getAllByUserId (UUID userId, Integer page, Integer size);
    Mono<AsyncOperationResponseDto> patch (UUID id, SiteRequestDto siteRequestDto);
    Mono<AsyncOperationResponseDto> update(UUID id, SiteRequestDto siteRequestDto);
}
