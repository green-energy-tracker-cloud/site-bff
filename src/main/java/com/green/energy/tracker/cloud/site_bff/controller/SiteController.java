package com.green.energy.tracker.cloud.site_bff.controller;

import com.green.energy.tracker.cloud.site_bff.service.v1.SiteService;
import com.green.energy.tracker.cloud.sitebff.web.api.SitesApi;
import com.green.energy.tracker.cloud.sitebff.web.model.AsyncOperationResponseDto;
import com.green.energy.tracker.cloud.sitebff.web.model.ListSitesResponseDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteRequestDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SiteController implements SitesApi {

    private final SiteService siteService;

    @Override
    public Mono<ResponseEntity<AsyncOperationResponseDto>> createSite(@Valid Mono<SiteRequestDto> siteRequestDto, ServerWebExchange exchange) {
        return siteRequestDto
                .flatMap(siteService::create)
                .map(response -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response));
    }

    @Override
    public Mono<ResponseEntity<AsyncOperationResponseDto>> deleteSite(UUID id, ServerWebExchange exchange) {
        return siteService.delete(id)
                .map(response -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response));
    }

    @Override
    public Mono<ResponseEntity<SiteResponseDto>> getSite(UUID id, ServerWebExchange exchange) {
        return siteService.get(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<ListSitesResponseDto>> listSites(UUID userId, Integer page, Integer size, ServerWebExchange exchange) {
        return siteService.getAllByUserId(userId, page, size)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<AsyncOperationResponseDto>> patchSite(UUID id, @Valid Mono<SiteRequestDto> siteRequestDto, ServerWebExchange exchange) {
        return siteRequestDto
                .flatMap(request-> siteService.patch(id, request))
                .map(response -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response));
    }

    @Override
    public Mono<ResponseEntity<AsyncOperationResponseDto>> updateSite(UUID id, @Valid Mono<SiteRequestDto> siteRequestDto, ServerWebExchange exchange) {
        return siteRequestDto
                .flatMap(request-> siteService.update(id, request))
                .map(response -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response));
    }
}