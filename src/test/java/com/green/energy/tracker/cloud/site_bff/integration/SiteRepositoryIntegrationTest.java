package com.green.energy.tracker.cloud.site_bff.integration;

import com.google.cloud.Timestamp;
import com.green.energy.tracker.cloud.site_bff.model.GeoLocationRead;
import com.green.energy.tracker.cloud.site_bff.model.SiteReadDocument;
import com.green.energy.tracker.cloud.site_bff.repository.SiteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SiteRepository.
 * These tests verify Firestore repository operations using the emulator
 * configured in the 'local' profile.
 */
class SiteRepositoryIntegrationTest extends IntegrationTestBase {

    @Autowired
    private SiteRepository siteRepository;

    private UUID testUserId;
    private SiteReadDocument testSite1;
    private SiteReadDocument testSite2;
    private SiteReadDocument testSite3;

    @BeforeEach
    void setUpTestData() {
        testUserId = UUID.randomUUID();

        testSite1 = SiteReadDocument.builder()
                .id(UUID.randomUUID().toString())
                .userId(testUserId.toString())
                .name("Solar Site Alpha")
                .address("100 Alpha Street, Milan, Italy")
                .location(GeoLocationRead.builder()
                        .latitude(45.4642)
                        .longitude(9.1900)
                        .build())
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        testSite2 = SiteReadDocument.builder()
                .id(UUID.randomUUID().toString())
                .userId(testUserId.toString())
                .name("Solar Site Beta")
                .address("200 Beta Avenue, Rome, Italy")
                .location(GeoLocationRead.builder()
                        .latitude(41.9028)
                        .longitude(12.4964)
                        .build())
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();

        testSite3 = SiteReadDocument.builder()
                .id(UUID.randomUUID().toString())
                .userId(UUID.randomUUID().toString()) // Different user
                .name("Solar Site Gamma")
                .address("300 Gamma Boulevard, Turin, Italy")
                .location(GeoLocationRead.builder()
                        .latitude(45.0703)
                        .longitude(7.6869)
                        .build())
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();
    }

    @AfterEach
    void cleanUpTestData() {
        siteRepository.deleteAll().block();
    }

    @Test
    void save_shouldPersistSiteToFirestore() {
        // Act
        Mono<SiteReadDocument> result = siteRepository.save(testSite1);

        // Assert
        StepVerifier.create(result)
                .assertNext(savedSite -> {
                    assertThat(savedSite).isNotNull();
                    assertThat(savedSite.getId()).isEqualTo(testSite1.getId());
                    assertThat(savedSite.getName()).isEqualTo("Solar Site Alpha");
                    assertThat(savedSite.getUserId()).isEqualTo(testUserId.toString());
                })
                .verifyComplete();
    }

    @Test
    void findById_whenSiteExists_shouldReturnSite() {
        // Arrange
        siteRepository.save(testSite1).block();

        // Act
        Mono<SiteReadDocument> result = siteRepository.findById(testSite1.getId());

        // Assert
        StepVerifier.create(result)
                .assertNext(site -> {
                    assertThat(site).isNotNull();
                    assertThat(site.getId()).isEqualTo(testSite1.getId());
                    assertThat(site.getName()).isEqualTo("Solar Site Alpha");
                    assertThat(site.getAddress()).isEqualTo("100 Alpha Street, Milan, Italy");
                    assertThat(site.getLocation()).isNotNull();
                    assertThat(site.getLocation().getLatitude()).isEqualTo(45.4642);
                    assertThat(site.getLocation().getLongitude()).isEqualTo(9.1900);
                })
                .verifyComplete();
    }

    @Test
    void findById_whenSiteDoesNotExist_shouldReturnEmpty() {
        // Arrange
        String nonExistentId = UUID.randomUUID().toString();

        // Act
        Mono<SiteReadDocument> result = siteRepository.findById(nonExistentId);

        // Assert
        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void findAllByUserId_shouldReturnSitesForSpecificUser() {
        // Arrange
        siteRepository.saveAll(Flux.just(testSite1, testSite2, testSite3)).blockLast();

        // Act
        Flux<SiteReadDocument> result = siteRepository.findAllByUserId(
                testUserId.toString(),
                PageRequest.of(0, 10)
        );

        // Assert
        StepVerifier.create(result)
                .assertNext(site -> {
                    assertThat(site.getUserId()).isEqualTo(testUserId.toString());
                    assertThat(site.getName()).isIn("Solar Site Alpha", "Solar Site Beta");
                })
                .assertNext(site -> {
                    assertThat(site.getUserId()).isEqualTo(testUserId.toString());
                    assertThat(site.getName()).isIn("Solar Site Alpha", "Solar Site Beta");
                })
                .verifyComplete();
    }

    @Test
    void findAllByUserId_withPagination_shouldReturnCorrectPage() {
        // Arrange: Create 5 sites for the same user
        for (int i = 0; i < 5; i++) {
            SiteReadDocument site = SiteReadDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(testUserId.toString())
                    .name("Site " + i)
                    .address("Address " + i)
                    .location(GeoLocationRead.builder()
                            .latitude(45.0 + i * 0.1)
                            .longitude(9.0 + i * 0.1)
                            .build())
                    .createdAt(Timestamp.now())
                    .updatedAt(Timestamp.now())
                    .build();
            siteRepository.save(site).block();
        }

        // Act: Get first page with size 2
        Flux<SiteReadDocument> resultPage1 = siteRepository.findAllByUserId(
                testUserId.toString(),
                PageRequest.of(0, 2)
        );

        // Assert
        StepVerifier.create(resultPage1)
                .expectNextCount(2)
                .verifyComplete();

        // Act: Get second page with size 2
        Flux<SiteReadDocument> resultPage2 = siteRepository.findAllByUserId(
                testUserId.toString(),
                PageRequest.of(1, 2)
        );

        // Assert
        StepVerifier.create(resultPage2)
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void findAllByUserId_whenNoSitesExist_shouldReturnEmpty() {
        // Arrange
        UUID userWithNoSites = UUID.randomUUID();

        // Act
        Flux<SiteReadDocument> result = siteRepository.findAllByUserId(
                userWithNoSites.toString(),
                PageRequest.of(0, 10)
        );

        // Assert
        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void countByUserId_shouldReturnCorrectCount() {
        // Arrange
        siteRepository.saveAll(Flux.just(testSite1, testSite2, testSite3)).blockLast();

        // Act
        Mono<Long> result = siteRepository.countByUserId(testUserId.toString());

        // Assert
        StepVerifier.create(result)
                .assertNext(count -> assertThat(count).isEqualTo(2))
                .verifyComplete();
    }

    @Test
    void countByUserId_whenNoSitesExist_shouldReturnZero() {
        // Arrange
        UUID userWithNoSites = UUID.randomUUID();

        // Act
        Mono<Long> result = siteRepository.countByUserId(userWithNoSites.toString());

        // Assert
        StepVerifier.create(result)
                .assertNext(count -> assertThat(count).isEqualTo(0))
                .verifyComplete();
    }

    @Test
    void deleteById_shouldRemoveSiteFromFirestore() {
        // Arrange
        siteRepository.save(testSite1).block();

        // Act
        Mono<Void> deleteResult = siteRepository.deleteById(testSite1.getId());

        // Assert
        StepVerifier.create(deleteResult)
                .verifyComplete();

        // Verify site was deleted
        StepVerifier.create(siteRepository.findById(testSite1.getId()))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void deleteAll_shouldRemoveAllSites() {
        // Arrange
        siteRepository.saveAll(Flux.just(testSite1, testSite2, testSite3)).blockLast();

        // Act & Assert - Delete all and verify the count matches
        siteRepository.deleteAll().block();

        // Verify all sites were deleted
        StepVerifier.create(siteRepository.findAll())
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void update_shouldModifyExistingSite() {
        // Arrange
        siteRepository.save(testSite1).block();

        // Modify the site
        testSite1.setName("Updated Solar Site Alpha");
        testSite1.setAddress("Updated Address");
        testSite1.setUpdatedAt(Timestamp.now());

        // Act
        Mono<SiteReadDocument> result = siteRepository.save(testSite1);

        // Assert
        StepVerifier.create(result)
                .assertNext(updatedSite -> {
                    assertThat(updatedSite.getId()).isEqualTo(testSite1.getId());
                    assertThat(updatedSite.getName()).isEqualTo("Updated Solar Site Alpha");
                    assertThat(updatedSite.getAddress()).isEqualTo("Updated Address");
                })
                .verifyComplete();

        // Verify the update persisted
        StepVerifier.create(siteRepository.findById(testSite1.getId()))
                .assertNext(site -> {
                    assertThat(site.getName()).isEqualTo("Updated Solar Site Alpha");
                    assertThat(site.getAddress()).isEqualTo("Updated Address");
                })
                .verifyComplete();
    }

    @Test
    void saveAll_shouldPersistMultipleSites() {
        // Act
        Flux<SiteReadDocument> result = siteRepository.saveAll(
                Flux.just(testSite1, testSite2, testSite3)
        );

        // Assert
        StepVerifier.create(result)
                .expectNextCount(3)
                .verifyComplete();

        // Verify all were saved
        StepVerifier.create(siteRepository.findAll())
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    void findAll_shouldReturnAllSites() {
        // Arrange
        siteRepository.saveAll(Flux.just(testSite1, testSite2, testSite3)).blockLast();

        // Act
        Flux<SiteReadDocument> result = siteRepository.findAll();

        // Assert
        StepVerifier.create(result)
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    void findAllByUserId_shouldRespectPageSize() {
        // Arrange: Create 10 sites
        for (int i = 0; i < 10; i++) {
            SiteReadDocument site = SiteReadDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(testUserId.toString())
                    .name("Site " + i)
                    .address("Address " + i)
                    .location(GeoLocationRead.builder()
                            .latitude(45.0 + i * 0.01)
                            .longitude(9.0 + i * 0.01)
                            .build())
                    .createdAt(Timestamp.now())
                    .updatedAt(Timestamp.now())
                    .build();
            siteRepository.save(site).block();
        }

        // Act: Request page with size 3
        Flux<SiteReadDocument> result = siteRepository.findAllByUserId(
                testUserId.toString(),
                PageRequest.of(0, 3)
        );

        // Assert: Should return exactly 3 items
        StepVerifier.create(result)
                .expectNextCount(3)
                .verifyComplete();
    }
}
