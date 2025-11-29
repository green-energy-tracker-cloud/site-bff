package com.green.energy.tracker.cloud.site_bff.integration;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.pubsub.core.publisher.PubSubPublisherTemplate;
import com.green.energy.tracker.cloud.site_bff.model.GeoLocationRead;
import com.green.energy.tracker.cloud.site_bff.model.SiteReadDocument;
import com.green.energy.tracker.cloud.site_bff.repository.SiteRepository;
import com.green.energy.tracker.cloud.sitebff.web.model.GeoLocationDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for SiteController.
 * These tests run with the 'local' profile, using emulated Firestore and PubSub.
 */
class SiteControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private SiteRepository siteRepository;

    @MockBean
    private PubSubPublisherTemplate pubSubPublisherTemplate;

    private SiteReadDocument testSite;
    private UUID testSiteId;
    private UUID testUserId;

    @BeforeEach
    void setUpTestData() {
        testSiteId = UUID.randomUUID();
        testUserId = UUID.randomUUID();

        testSite = SiteReadDocument.builder()
                .id(testSiteId.toString())
                .userId(testUserId.toString())
                .name("Test Solar Site")
                .address("123 Green Energy St, Milan, Italy")
                .location(GeoLocationRead.builder()
                        .latitude(45.4642)
                        .longitude(9.1900)
                        .build())
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        // Mock PubSub publisher to avoid actual message publishing
        when(pubSubPublisherTemplate.publish(anyString(), any()))
                .thenReturn(Mono.just(UUID.randomUUID().toString()).toFuture());
    }

    @AfterEach
    void cleanUpTestData() {
        // Clean up test data from Firestore emulator
        siteRepository.deleteAll().block();
    }

    @Test
    void getSite_whenSiteExists_shouldReturnSiteWithOk() {
        // Arrange
        siteRepository.save(testSite).block();

        // Act & Assert
        webTestClient.get()
                .uri("/sites/{id}", testSiteId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id").isEqualTo(testSiteId.toString())
                .jsonPath("$.name").isEqualTo("Test Solar Site")
                .jsonPath("$.address").isEqualTo("123 Green Energy St, Milan, Italy")
                .jsonPath("$.user_id").isEqualTo(testUserId.toString())
                .jsonPath("$.location.latitude").isEqualTo(45.4642)
                .jsonPath("$.location.longitude").isEqualTo(9.1900);
    }

    @Test
    void getSite_whenSiteDoesNotExist_shouldReturnNotFound() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act & Assert
        webTestClient.get()
                .uri("/sites/{id}", nonExistentId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void listSites_whenSitesExistForUser_shouldReturnSitesList() {
        // Arrange
        SiteReadDocument site1 = SiteReadDocument.builder()
                .id(UUID.randomUUID().toString())
                .userId(testUserId.toString())
                .name("Site 1")
                .address("Address 1")
                .location(GeoLocationRead.builder().latitude(45.0).longitude(9.0).build())
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        SiteReadDocument site2 = SiteReadDocument.builder()
                .id(UUID.randomUUID().toString())
                .userId(testUserId.toString())
                .name("Site 2")
                .address("Address 2")
                .location(GeoLocationRead.builder().latitude(45.1).longitude(9.1).build())
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        siteRepository.saveAll(Flux.just(site1, site2)).blockLast();

        // Act & Assert
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sites")
                        .queryParam("user_id", testUserId)
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.total_count").isEqualTo(2)
                .jsonPath("$.total_pages").isEqualTo(1)
                .jsonPath("$.sites").isArray()
                .jsonPath("$.sites.length()").isEqualTo(2);
    }

    @Test
    void listSites_whenNoSitesExistForUser_shouldReturnEmptyList() {
        // Arrange
        UUID userWithNoSites = UUID.randomUUID();

        // Act & Assert
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sites")
                        .queryParam("user_id", userWithNoSites)
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total_count").isEqualTo(0)
                .jsonPath("$.total_pages").isEqualTo(0)
                .jsonPath("$.sites").isArray()
                .jsonPath("$.sites.length()").isEqualTo(0);
    }

    @Test
    void createSite_withValidRequest_shouldReturnAccepted() {
        // Arrange
        SiteRequestDto request = new SiteRequestDto();
        request.setUserId(testUserId);
        request.setName("New Solar Site");
        request.setAddress("456 Renewable Ave, Rome, Italy");

        GeoLocationDto location = new GeoLocationDto();
        location.setLatitude(41.9028);
        location.setLongitude(12.4964);
        request.setLocation(location);

        // Act & Assert
        webTestClient.post()
                .uri("/sites")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED")
                .jsonPath("$.id").exists()
                .jsonPath("$.message").exists();
    }

    @Test
    void updateSite_withValidRequest_shouldReturnAccepted() {
        // Arrange
        SiteRequestDto request = new SiteRequestDto();
        request.setUserId(testUserId);
        request.setName("Updated Solar Site");
        request.setAddress("Updated Address");

        GeoLocationDto location = new GeoLocationDto();
        location.setLatitude(45.5);
        location.setLongitude(9.2);
        request.setLocation(location);

        // Act & Assert
        webTestClient.put()
                .uri("/sites/{id}", testSiteId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED")
                .jsonPath("$.id").isEqualTo(testSiteId.toString())
                .jsonPath("$.message").exists();
    }

    @Test
    void patchSite_withValidRequest_shouldReturnAccepted() {
        // Arrange
        SiteRequestDto request = new SiteRequestDto();
        request.setUserId(testUserId);
        request.setName("Patched Solar Site");
        request.setAddress("Patched Address");

        GeoLocationDto location = new GeoLocationDto();
        location.setLatitude(45.6);
        location.setLongitude(9.3);
        request.setLocation(location);

        // Act & Assert
        webTestClient.patch()
                .uri("/sites/{id}", testSiteId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED")
                .jsonPath("$.id").isEqualTo(testSiteId.toString())
                .jsonPath("$.message").exists();
    }

    @Test
    void deleteSite_withValidId_shouldReturnAccepted() {
        // Act & Assert
        webTestClient.delete()
                .uri("/sites/{id}", testSiteId)
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED")
                .jsonPath("$.id").isEqualTo(testSiteId.toString())
                .jsonPath("$.message").exists();
    }

    @Test
    void listSites_withPagination_shouldReturnCorrectPage() {
        // Arrange: Create 5 sites
        for (int i = 0; i < 5; i++) {
            SiteReadDocument site = SiteReadDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(testUserId.toString())
                    .name("Site " + i)
                    .address("Address " + i)
                    .location(GeoLocationRead.builder().latitude(45.0 + i).longitude(9.0 + i).build())
                    .createdAt(Timestamp.now())
                    .updatedAt(Timestamp.now())
                    .build();
            siteRepository.save(site).block();
        }

        // Act & Assert: Get first page with size 2
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sites")
                        .queryParam("user_id", testUserId)
                        .queryParam("page", 0)
                        .queryParam("size", 2)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total_count").isEqualTo(5)
                .jsonPath("$.total_pages").isEqualTo(3)
                .jsonPath("$.sites.length()").isEqualTo(2);

        // Act & Assert: Get second page with size 2
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sites")
                        .queryParam("user_id", testUserId)
                        .queryParam("page", 1)
                        .queryParam("size", 2)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total_count").isEqualTo(5)
                .jsonPath("$.total_pages").isEqualTo(3)
                .jsonPath("$.sites.length()").isEqualTo(2);
    }
}
