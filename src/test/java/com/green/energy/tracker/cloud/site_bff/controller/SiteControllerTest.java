package com.green.energy.tracker.cloud.site_bff.controller;

import com.green.energy.tracker.cloud.site_bff.service.v1.SiteService;
import com.green.energy.tracker.cloud.sitebff.web.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(SiteController.class)
class SiteControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private SiteService siteService;

    private SiteRequestDto createValidSiteRequestDto() {
        var siteRequestDto = new SiteRequestDto();
        siteRequestDto.setUserId(UUID.randomUUID());
        siteRequestDto.setName("test");
        siteRequestDto.setAddress("test");
        var location = new GeoLocationDto();
        location.setLatitude(0.0);
        location.setLongitude(0.0);
        siteRequestDto.setLocation(location);
        return siteRequestDto;
    }

    @Test
    void createSite_shouldReturnAccepted() {
        var siteRequestDto = createValidSiteRequestDto();
        var asyncResponse = new AsyncOperationResponseDto();

        when(siteService.create(any(SiteRequestDto.class))).thenReturn(Mono.just(asyncResponse));

        webTestClient.post().uri("/sites")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(siteRequestDto), SiteRequestDto.class)
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void deleteSite_shouldReturnAccepted() {
        var id = UUID.randomUUID();
        var asyncResponse = new AsyncOperationResponseDto();

        when(siteService.delete(id)).thenReturn(Mono.just(asyncResponse));

        webTestClient.delete().uri("/sites/{id}", id)
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void getSite_shouldReturnSite() {
        var id = UUID.randomUUID();
        var siteResponseDto = new SiteResponseDto();

        when(siteService.get(id)).thenReturn(Mono.just(siteResponseDto));

        webTestClient.get().uri("/sites/{id}", id)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void listSites_shouldReturnSites() {
        var userId = UUID.randomUUID();
        var page = 0;
        var size = 10;
        var listSitesResponseDto = new ListSitesResponseDto();

        when(siteService.getAllByUserId(userId, page, size)).thenReturn(Mono.just(listSitesResponseDto));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/sites")
                        .queryParam("user_id", userId) // FIX: Use snake_case to match the API contract
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void patchSite_shouldReturnAccepted() {
        var id = UUID.randomUUID();
        var siteRequestDto = createValidSiteRequestDto();
        var asyncResponse = new AsyncOperationResponseDto();

        when(siteService.patch(any(), any(SiteRequestDto.class))).thenReturn(Mono.just(asyncResponse));

        webTestClient.patch().uri("/sites/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(siteRequestDto), SiteRequestDto.class)
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void updateSite_shouldReturnAccepted() {
        var id = UUID.randomUUID();
        var siteRequestDto = createValidSiteRequestDto();
        var asyncResponse = new AsyncOperationResponseDto();

        when(siteService.update(any(), any(SiteRequestDto.class))).thenReturn(Mono.just(asyncResponse));

        webTestClient.put().uri("/sites/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(siteRequestDto), SiteRequestDto.class)
                .exchange()
                .expectStatus().isAccepted();
    }
}
