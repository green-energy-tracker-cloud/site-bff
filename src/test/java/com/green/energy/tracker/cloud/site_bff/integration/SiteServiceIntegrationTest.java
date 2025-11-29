package com.green.energy.tracker.cloud.site_bff.integration;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.pubsub.core.publisher.PubSubPublisherTemplate;
import com.green.energy.tracker.cloud.site_bff.model.GeoLocationRead;
import com.green.energy.tracker.cloud.site_bff.model.SiteReadDocument;
import com.green.energy.tracker.cloud.site_bff.repository.SiteRepository;
import com.green.energy.tracker.cloud.site_bff.service.SiteService;
import com.green.energy.tracker.cloud.sitebff.web.model.AsyncOperationResponseDto;
import com.green.energy.tracker.cloud.sitebff.web.model.GeoLocationDto;
import com.green.energy.tracker.cloud.sitebff.web.model.ListSitesResponseDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteRequestDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteResponseDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for SiteService.
 * These tests verify the service layer functionality with actual repository interactions
 * using the 'local' profile configuration.
 */
class SiteServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    @Qualifier("SiteServiceV1")
    private SiteService siteService;

    @Autowired
    private SiteRepository siteRepository;

    @MockBean
    private PubSubPublisherTemplate pubSubPublisherTemplate;

    @Autowired
    private ReactiveRedisTemplate<String, SiteResponseDto> siteRedisTemplate;

    @Autowired
    private ReactiveRedisTemplate<String, ListSitesResponseDto> siteListRedisTemplate;

    private UUID testSiteId;
    private UUID testUserId;
    private SiteReadDocument testSite;

    @BeforeEach
    void setUpTestData() {
        testSiteId = UUID.randomUUID();
        testUserId = UUID.randomUUID();

        testSite = SiteReadDocument.builder()
                .id(testSiteId.toString())
                .userId(testUserId.toString())
                .name("Integration Test Site")
                .address("123 Test Street, Test City")
                .location(GeoLocationRead.builder()
                        .latitude(45.4642)
                        .longitude(9.1900)
                        .build())
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        // Mock PubSub publisher
        when(pubSubPublisherTemplate.publish(anyString(), any()))
                .thenReturn(Mono.just(UUID.randomUUID().toString()).toFuture());
    }

    @AfterEach
    void cleanUpTestData() {
        // Clean up Firestore
        siteRepository.deleteAll().block();

        // Clean up Redis cache
        siteRedisTemplate.delete("site-test:" + testSiteId).block();
        siteListRedisTemplate.keys("site-test:user:*").flatMap(siteListRedisTemplate::delete).blockLast();
    }

    @Test
    void create_withValidRequest_shouldReturnAcceptedResponse() {
        // Arrange
        SiteRequestDto request = createValidSiteRequest();

        // Act
        Mono<AsyncOperationResponseDto> result = siteService.create(request);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.getStatus()).isEqualTo(AsyncOperationResponseDto.StatusEnum.ACCEPTED);
                    assertThat(response.getId()).isNotNull();
                    assertThat(response.getMessage()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void get_whenSiteExists_shouldReturnSite() {
        // Arrange
        siteRepository.save(testSite).block();

        // Act
        Mono<SiteResponseDto> result = siteService.get(testSiteId);

        // Assert
        StepVerifier.create(result)
                .assertNext(site -> {
                    assertThat(site).isNotNull();
                    assertThat(site.getId()).isEqualTo(testSiteId);
                    assertThat(site.getName()).isEqualTo("Integration Test Site");
                    assertThat(site.getAddress()).isEqualTo("123 Test Street, Test City");
                    assertThat(site.getUserId()).isEqualTo(testUserId);
                    assertThat(site.getLocation()).isNotNull();
                    assertThat(site.getLocation().getLatitude()).isEqualTo(45.4642);
                    assertThat(site.getLocation().getLongitude()).isEqualTo(9.1900);
                })
                .verifyComplete();
    }

    @Test
    void get_whenSiteDoesNotExist_shouldReturnError() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act
        Mono<SiteResponseDto> result = siteService.get(nonExistentId);

        // Assert
        StepVerifier.create(result)
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void get_shouldUseCacheOnSecondCall() {
        // Arrange
        siteRepository.save(testSite).block();

        // Act: First call - should fetch from DB and cache
        SiteResponseDto firstCall = siteService.get(testSiteId).block();

        // Delete from repository to verify cache is used
        siteRepository.deleteById(testSiteId.toString()).block();

        // Act: Second call - should fetch from cache
        Mono<SiteResponseDto> result = siteService.get(testSiteId);

        // Assert
        StepVerifier.create(result)
                .assertNext(site -> {
                    assertThat(site).isNotNull();
                    assertThat(site.getId()).isEqualTo(testSiteId);
                    assertThat(site.getName()).isEqualTo("Integration Test Site");
                })
                .verifyComplete();
    }

    @Test
    void getAllByUserId_whenSitesExist_shouldReturnPagedList() {
        // Arrange
        SiteReadDocument site1 = createTestSite("Site 1", 45.0, 9.0);
        SiteReadDocument site2 = createTestSite("Site 2", 45.1, 9.1);
        SiteReadDocument site3 = createTestSite("Site 3", 45.2, 9.2);

        siteRepository.saveAll(Flux.just(site1, site2, site3)).blockLast();

        // Act
        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(testUserId, 0, 10);

        // Assert
        StepVerifier.create(result)
                .assertNext(listResponse -> {
                    assertThat(listResponse).isNotNull();
                    assertThat(listResponse.getTotalCount()).isEqualTo(3);
                    assertThat(listResponse.getTotalPages()).isEqualTo(1);
                    assertThat(listResponse.getSites()).hasSize(3);
                    assertThat(listResponse.getSites())
                            .extracting(SiteResponseDto::getName)
                            .containsExactlyInAnyOrder("Site 1", "Site 2", "Site 3");
                })
                .verifyComplete();
    }

    @Test
    void getAllByUserId_withPagination_shouldReturnCorrectPage() {
        // Arrange: Create 5 sites
        for (int i = 0; i < 5; i++) {
            SiteReadDocument site = createTestSite("Site " + i, 45.0 + i, 9.0 + i);
            siteRepository.save(site).block();
        }

        // Act: Get first page with size 2
        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(testUserId, 0, 2);

        // Assert
        StepVerifier.create(result)
                .assertNext(listResponse -> {
                    assertThat(listResponse).isNotNull();
                    assertThat(listResponse.getTotalCount()).isEqualTo(5);
                    assertThat(listResponse.getTotalPages()).isEqualTo(3);
                    assertThat(listResponse.getSites()).hasSize(2);
                })
                .verifyComplete();
    }

    @Test
    void getAllByUserId_shouldUseCacheOnSecondCall() {
        // Arrange
        SiteReadDocument site1 = createTestSite("Site 1", 45.0, 9.0);
        siteRepository.save(site1).block();

        // Act: First call - should fetch from DB and cache
        ListSitesResponseDto firstCall = siteService.getAllByUserId(testUserId, 0, 10).block();

        // Delete from repository to verify cache is used
        siteRepository.deleteAll().block();

        // Act: Second call - should fetch from cache
        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(testUserId, 0, 10);

        // Assert
        StepVerifier.create(result)
                .assertNext(listResponse -> {
                    assertThat(listResponse).isNotNull();
                    assertThat(listResponse.getTotalCount()).isEqualTo(1);
                    assertThat(listResponse.getSites()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void getAllByUserId_withDefaultPagination_shouldUseDefaults() {
        // Arrange
        SiteReadDocument site = createTestSite("Site", 45.0, 9.0);
        siteRepository.save(site).block();

        // Act: Call with null pagination parameters
        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(testUserId, null, null);

        // Assert
        StepVerifier.create(result)
                .assertNext(listResponse -> {
                    assertThat(listResponse).isNotNull();
                    assertThat(listResponse.getTotalCount()).isEqualTo(1);
                    assertThat(listResponse.getSites()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void update_withValidRequest_shouldReturnAcceptedResponse() {
        // Arrange
        SiteRequestDto request = createValidSiteRequest();

        // Act
        Mono<AsyncOperationResponseDto> result = siteService.update(testSiteId, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.getStatus()).isEqualTo(AsyncOperationResponseDto.StatusEnum.ACCEPTED);
                    assertThat(response.getId()).isEqualTo(testSiteId);
                    assertThat(response.getMessage()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void patch_withValidRequest_shouldReturnAcceptedResponse() {
        // Arrange
        SiteRequestDto request = createValidSiteRequest();

        // Act
        Mono<AsyncOperationResponseDto> result = siteService.patch(testSiteId, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.getStatus()).isEqualTo(AsyncOperationResponseDto.StatusEnum.ACCEPTED);
                    assertThat(response.getId()).isEqualTo(testSiteId);
                    assertThat(response.getMessage()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void delete_withValidId_shouldReturnAcceptedResponse() {
        // Act
        Mono<AsyncOperationResponseDto> result = siteService.delete(testSiteId);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.getStatus()).isEqualTo(AsyncOperationResponseDto.StatusEnum.ACCEPTED);
                    assertThat(response.getId()).isEqualTo(testSiteId);
                    assertThat(response.getMessage()).isNotNull();
                })
                .verifyComplete();
    }

    // Helper methods

    private SiteRequestDto createValidSiteRequest() {
        SiteRequestDto request = new SiteRequestDto();
        request.setUserId(testUserId);
        request.setName("New Test Site");
        request.setAddress("456 New Test Ave");

        GeoLocationDto location = new GeoLocationDto();
        location.setLatitude(41.9028);
        location.setLongitude(12.4964);
        request.setLocation(location);

        return request;
    }

    private SiteReadDocument createTestSite(String name, double latitude, double longitude) {
        return SiteReadDocument.builder()
                .id(UUID.randomUUID().toString())
                .userId(testUserId.toString())
                .name(name)
                .address("Test Address for " + name)
                .location(GeoLocationRead.builder()
                        .latitude(latitude)
                        .longitude(longitude)
                        .build())
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();
    }
}
