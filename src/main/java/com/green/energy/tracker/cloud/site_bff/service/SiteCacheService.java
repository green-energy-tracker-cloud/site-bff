package com.green.energy.tracker.cloud.site_bff.service;

import com.green.energy.tracker.cloud.sitebff.web.model.ListSitesResponseDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteResponseDto;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Supplier;

public interface SiteCacheService {
    Mono<SiteResponseDto> getSite(UUID id, Supplier<Mono<SiteResponseDto>> dbFallback);
    Mono<ListSitesResponseDto> getSitesByUserId(UUID userId, Integer page, Integer size, Supplier<Mono<ListSitesResponseDto>> dbFallback);
}
