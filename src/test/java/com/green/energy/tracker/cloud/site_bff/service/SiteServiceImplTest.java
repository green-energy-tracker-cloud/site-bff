package com.green.energy.tracker.cloud.site_bff.service;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.pubsub.core.publisher.PubSubPublisherTemplate;
import com.green.energy.tracker.cloud.site_bff.model.GeoLocationRead;
import com.green.energy.tracker.cloud.site_bff.model.SiteMapper;
import com.green.energy.tracker.cloud.site_bff.model.SiteReadDocument;
import com.green.energy.tracker.cloud.site_bff.repository.SiteRepository;
import com.green.energy.tracker.cloud.sitebff.web.model.*;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SiteServiceImplTest {

    @Mock
    private PubSubPublisherTemplate publisherTemplate;

    @Mock
    private SiteMapper siteMapper;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private SiteCacheService siteCacheService;

    @Mock
    private ReactiveCircuitBreaker cbPubSub;

    @Mock
    private ReactiveCircuitBreaker cbFirestore;

    private SiteServiceImpl siteService;

    private Retry retryPubSub;
    private Retry retryFirestore;

    private static final String SITE_EVENTS_TOPIC = "site-events";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 10;

    @BeforeEach
    void setUp() {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .build();

        retryPubSub = Retry.of("pubsub", retryConfig);
        retryFirestore = Retry.of("firestore", retryConfig);

        siteService = new SiteServiceImpl(
                publisherTemplate,
                siteMapper,
                siteRepository,
                siteCacheService,
                cbPubSub,
                cbFirestore,
                retryPubSub,
                retryFirestore
        );

        ReflectionTestUtils.setField(siteService, "siteEventsTopic", SITE_EVENTS_TOPIC);
        ReflectionTestUtils.setField(siteService, "defaultPage", DEFAULT_PAGE);
        ReflectionTestUtils.setField(siteService, "defaultPageSize", DEFAULT_PAGE_SIZE);
    }

    @Test
    void create_ShouldPublishEventAndReturnAsyncResponse() {
        // Given
        SiteRequestDto request = createSiteRequestDto();
        String messageId = "msg-123";

        when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any()))
                .thenReturn(CompletableFuture.completedFuture(messageId));
        when(cbPubSub.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Mono<AsyncOperationResponseDto> result = siteService.create(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getId()).isNotNull();
                    assertThat(response.getStatus()).isEqualTo(AsyncOperationResponseDto.StatusEnum.ACCEPTED);
                    assertThat(response.getMessage()).contains(messageId);
                })
                .verifyComplete();

        verify(publisherTemplate).publish(eq(SITE_EVENTS_TOPIC), any());
    }

    @Test
    void create_WhenPublishFails_ShouldReturnError() {
        // Given
        SiteRequestDto request = createSiteRequestDto();

        when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Publish failed")));
        when(cbPubSub.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Mono<AsyncOperationResponseDto> result = siteService.create(request);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void delete_ShouldPublishDeleteEventAndReturnAsyncResponse() {
        // Given
        UUID siteId = UUID.randomUUID();
        String messageId = "msg-delete-123";

        when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any()))
                .thenReturn(CompletableFuture.completedFuture(messageId));
        when(cbPubSub.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Mono<AsyncOperationResponseDto> result = siteService.delete(siteId);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(siteId);
                    assertThat(response.getStatus()).isEqualTo(AsyncOperationResponseDto.StatusEnum.ACCEPTED);
                    assertThat(response.getMessage()).contains(messageId);
                })
                .verifyComplete();

        verify(publisherTemplate).publish(eq(SITE_EVENTS_TOPIC), any());
    }

    @Test
    void get_ShouldUseCacheService() {
        // Given
        UUID siteId = UUID.randomUUID();
        SiteResponseDto expectedResponse = createSiteResponseDto(siteId);

        when(siteCacheService.getSite(eq(siteId), any()))
                .thenReturn(Mono.just(expectedResponse));

        // When
        Mono<SiteResponseDto> result = siteService.get(siteId);

        // Then
        StepVerifier.create(result)
                .expectNext(expectedResponse)
                .verifyComplete();

        verify(siteCacheService).getSite(eq(siteId), any());
    }

    @Test
    void get_WhenSiteNotFoundInDb_ShouldReturnNotFoundError() {
        // Given
        UUID siteId = UUID.randomUUID();

        when(siteCacheService.getSite(eq(siteId), any()))
                .thenAnswer(invocation -> {
                    var supplier = invocation.getArgument(1, java.util.function.Supplier.class);
                    return supplier.get();
                });

        when(siteRepository.findById(siteId.toString()))
                .thenReturn(Mono.empty());
        when(cbFirestore.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Mono<SiteResponseDto> result = siteService.get(siteId);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException &&
                                ((ResponseStatusException) error).getStatusCode().equals(HttpStatus.NOT_FOUND))
                .verify();
    }

    @Test
    void get_WhenSiteFoundInDb_ShouldMapAndReturnDto() {
        // Given
        UUID siteId = UUID.randomUUID();
        SiteReadDocument document = createSiteReadDocument(siteId);
        SiteResponseDto expectedResponse = createSiteResponseDto(siteId);

        when(siteCacheService.getSite(eq(siteId), any()))
                .thenAnswer(invocation -> {
                    var supplier = invocation.getArgument(1, java.util.function.Supplier.class);
                    return supplier.get();
                });

        when(siteRepository.findById(siteId.toString()))
                .thenReturn(Mono.just(document));
        when(siteMapper.toDto(document))
                .thenReturn(expectedResponse);
        when(cbFirestore.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Mono<SiteResponseDto> result = siteService.get(siteId);

        // Then
        StepVerifier.create(result)
                .expectNext(expectedResponse)
                .verifyComplete();

        verify(siteRepository).findById(siteId.toString());
        verify(siteMapper).toDto(document);
    }

    @Test
    void getAllByUserId_ShouldUseCacheService() {
        // Given
        UUID userId = UUID.randomUUID();
        Integer page = 0;
        Integer size = 10;
        ListSitesResponseDto expectedResponse = createListSitesResponseDto();

        when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(size), any()))
                .thenReturn(Mono.just(expectedResponse));

        // When
        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

        // Then
        StepVerifier.create(result)
                .expectNext(expectedResponse)
                .verifyComplete();

        verify(siteCacheService).getSitesByUserId(eq(userId), eq(page), eq(size), any());
    }

    @Test
    void getAllByUserId_WithNullPageAndSize_ShouldUseDefaults() {
        // Given
        UUID userId = UUID.randomUUID();
        ListSitesResponseDto expectedResponse = createListSitesResponseDto();

        when(siteCacheService.getSitesByUserId(eq(userId), eq(DEFAULT_PAGE), eq(DEFAULT_PAGE_SIZE), any()))
                .thenReturn(Mono.just(expectedResponse));

        // When
        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, null, null);

        // Then
        StepVerifier.create(result)
                .expectNext(expectedResponse)
                .verifyComplete();

        verify(siteCacheService).getSitesByUserId(eq(userId), eq(DEFAULT_PAGE), eq(DEFAULT_PAGE_SIZE), any());
    }

    @Test
    void getAllByUserId_WhenFetchingFromDb_ShouldReturnPaginatedResults() {
        // Given
        UUID userId = UUID.randomUUID();
        Integer page = 0;
        Integer size = 10;
        SiteReadDocument doc1 = createSiteReadDocument(UUID.randomUUID());
        SiteReadDocument doc2 = createSiteReadDocument(UUID.randomUUID());
        SiteResponseDto dto1 = createSiteResponseDto(UUID.fromString(doc1.getId()));
        SiteResponseDto dto2 = createSiteResponseDto(UUID.fromString(doc2.getId()));

        when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(size), any()))
                .thenAnswer(invocation -> {
                    var supplier = invocation.getArgument(3, java.util.function.Supplier.class);
                    return supplier.get();
                });

        when(siteRepository.countByUserId(userId.toString()))
                .thenReturn(Mono.just(2L));
        when(siteRepository.findAllByUserId(eq(userId.toString()), any(Pageable.class)))
                .thenReturn(Flux.just(doc1, doc2));
        when(siteMapper.toDto(doc1)).thenReturn(dto1);
        when(siteMapper.toDto(doc2)).thenReturn(dto2);
        when(cbFirestore.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cbFirestore.run(any(Flux.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getTotalCount()).isEqualTo(2);
                    assertThat(response.getTotalPages()).isEqualTo(1);
                    assertThat(response.getSites()).hasSize(2);
                })
                .verifyComplete();

        verify(siteRepository).countByUserId(userId.toString());
        verify(siteRepository).findAllByUserId(eq(userId.toString()), any(Pageable.class));
    }

    @Test
    void getAllByUserId_WhenCalculatingTotalPages_ShouldRoundUpCorrectly() {
        // Given
        UUID userId = UUID.randomUUID();
        Integer page = 0;
        Integer size = 10;

        when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(size), any()))
                .thenAnswer(invocation -> {
                    var supplier = invocation.getArgument(3, java.util.function.Supplier.class);
                    return supplier.get();
                });

        when(siteRepository.countByUserId(userId.toString()))
                .thenReturn(Mono.just(25L));
        when(siteRepository.findAllByUserId(eq(userId.toString()), any(Pageable.class)))
                .thenReturn(Flux.empty());
        when(cbFirestore.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cbFirestore.run(any(Flux.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getTotalCount()).isEqualTo(25);
                    assertThat(response.getTotalPages()).isEqualTo(3); // ceil(25/10) = 3
                })
                .verifyComplete();
    }

    @Test
    void patch_ShouldPublishPatchEventAndReturnAsyncResponse() {
        // Given
        UUID siteId = UUID.randomUUID();
        SiteRequestDto request = createSiteRequestDto();
        String messageId = "msg-patch-123";

        when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any()))
                .thenReturn(CompletableFuture.completedFuture(messageId));
        when(cbPubSub.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Mono<AsyncOperationResponseDto> result = siteService.patch(siteId, request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(siteId);
                    assertThat(response.getStatus()).isEqualTo(AsyncOperationResponseDto.StatusEnum.ACCEPTED);
                    assertThat(response.getMessage()).contains(messageId);
                })
                .verifyComplete();

        verify(publisherTemplate).publish(eq(SITE_EVENTS_TOPIC), any());
    }

    @Test
    void update_ShouldPublishUpdateEventAndReturnAsyncResponse() {
        // Given
        UUID siteId = UUID.randomUUID();
        SiteRequestDto request = createSiteRequestDto();
        String messageId = "msg-update-123";

        when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any()))
                .thenReturn(CompletableFuture.completedFuture(messageId));
        when(cbPubSub.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Mono<AsyncOperationResponseDto> result = siteService.update(siteId, request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(siteId);
                    assertThat(response.getStatus()).isEqualTo(AsyncOperationResponseDto.StatusEnum.ACCEPTED);
                    assertThat(response.getMessage()).contains(messageId);
                })
                .verifyComplete();

        verify(publisherTemplate).publish(eq(SITE_EVENTS_TOPIC), any());
    }

    @Test
    void create_WhenCircuitBreakerIsOpen_ShouldReturnServiceUnavailable() {
        // Given
        SiteRequestDto request = createSiteRequestDto();

        when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any()))
                .thenReturn(CompletableFuture.completedFuture("msg-123"));

        when(cbPubSub.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<Throwable, Mono> fallback = invocation.getArgument(1);
                    return fallback.apply(new RuntimeException("Circuit breaker open"));
                });

        // When
        Mono<AsyncOperationResponseDto> result = siteService.create(request);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException &&
                                ((ResponseStatusException) error).getStatusCode().equals(HttpStatus.SERVICE_UNAVAILABLE))
                .verify();
    }

    @Test
    void get_WhenCircuitBreakerIsOpen_ShouldReturnServiceUnavailable() {
        // Given
        UUID siteId = UUID.randomUUID();

        when(siteCacheService.getSite(eq(siteId), any()))
                .thenAnswer(invocation -> {
                    var supplier = invocation.getArgument(1, java.util.function.Supplier.class);
                    return supplier.get();
                });

        when(siteRepository.findById(siteId.toString()))
                .thenReturn(Mono.just(createSiteReadDocument(siteId)));

        when(cbFirestore.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<Throwable, Mono> fallback = invocation.getArgument(1);
                    return fallback.apply(new RuntimeException("Circuit breaker open"));
                });

        // When
        Mono<SiteResponseDto> result = siteService.get(siteId);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException &&
                                ((ResponseStatusException) error).getStatusCode().equals(HttpStatus.SERVICE_UNAVAILABLE))
                .verify();
    }

    @Test
    void getAllByUserId_WhenCircuitBreakerIsOpenOnCount_ShouldReturnServiceUnavailable() {
        // Given
        UUID userId = UUID.randomUUID();

        when(siteCacheService.getSitesByUserId(eq(userId), anyInt(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    var supplier = invocation.getArgument(3, java.util.function.Supplier.class);
                    return supplier.get();
                });

        when(siteRepository.countByUserId(userId.toString()))
                .thenReturn(Mono.just(10L));
        when(siteRepository.findAllByUserId(eq(userId.toString()), any(Pageable.class)))
                .thenReturn(Flux.empty());

        when(cbFirestore.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<Throwable, Mono> fallback = invocation.getArgument(1);
                    return fallback.apply(new RuntimeException("Circuit breaker open"));
                });

        // When
        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, 0, 10);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException &&
                                ((ResponseStatusException) error).getStatusCode().equals(HttpStatus.SERVICE_UNAVAILABLE))
                .verify();
    }

    @Test
    void getAllByUserId_WhenCircuitBreakerIsOpenOnFindAll_ShouldReturnServiceUnavailable() {
        // Given
        UUID userId = UUID.randomUUID();

        when(siteCacheService.getSitesByUserId(eq(userId), anyInt(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    var supplier = invocation.getArgument(3, java.util.function.Supplier.class);
                    return supplier.get();
                });

        when(siteRepository.countByUserId(userId.toString()))
                .thenReturn(Mono.just(10L));
        when(siteRepository.findAllByUserId(eq(userId.toString()), any(Pageable.class)))
                .thenReturn(Flux.just(createSiteReadDocument(UUID.randomUUID())));

        when(cbFirestore.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(cbFirestore.run(any(Flux.class), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<Throwable, Flux> fallback = invocation.getArgument(1);
                    return fallback.apply(new RuntimeException("Circuit breaker open"));
                });

        // When
        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, 0, 10);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException &&
                                ((ResponseStatusException) error).getStatusCode().equals(HttpStatus.SERVICE_UNAVAILABLE))
                .verify();
    }

    @Test
    void getAllByUserId_WithNegativePage_ShouldUseDefaultPage() {
        // Given
        UUID userId = UUID.randomUUID();
        Integer page = -1;
        Integer size = 10;
        ListSitesResponseDto expectedResponse = createListSitesResponseDto();

        when(siteCacheService.getSitesByUserId(eq(userId), eq(DEFAULT_PAGE), eq(size), any()))
                .thenReturn(Mono.just(expectedResponse));

        // When
        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

        // Then
        StepVerifier.create(result)
                .expectNext(expectedResponse)
                .verifyComplete();

        verify(siteCacheService).getSitesByUserId(eq(userId), eq(DEFAULT_PAGE), eq(size), any());
    }

    @Test
    void getAllByUserId_WithZeroSize_ShouldUseDefaultSize() {
        // Given
        UUID userId = UUID.randomUUID();
        Integer page = 0;
        Integer size = 0;
        ListSitesResponseDto expectedResponse = createListSitesResponseDto();

        when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(DEFAULT_PAGE_SIZE), any()))
                .thenReturn(Mono.just(expectedResponse));

        // When
        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

        // Then
        StepVerifier.create(result)
                .expectNext(expectedResponse)
                .verifyComplete();

        verify(siteCacheService).getSitesByUserId(eq(userId), eq(page), eq(DEFAULT_PAGE_SIZE), any());
    }

    @Test
    void publishEvent_WhenNullPayloadWithNonDeleteEvent_ShouldReturnError() throws Exception {
        // Given
        String id = UUID.randomUUID().toString();

        // Use reflection to access private method
        var publishEventMethod = SiteServiceImpl.class.getDeclaredMethod(
                "publishEvent",
                com.green.energy.tracker.cloud.site.v1.SiteEventType.class,
                String.class,
                com.green.energy.tracker.cloud.site.v1.Site.class
        );
        publishEventMethod.setAccessible(true);

        // When
        @SuppressWarnings("unchecked")
        Mono<AsyncOperationResponseDto> result = (Mono<AsyncOperationResponseDto>) publishEventMethod.invoke(
                siteService,
                com.green.energy.tracker.cloud.site.v1.SiteEventType.CREATE,
                id,
                null
        );

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(error ->
                        error instanceof IllegalArgumentException &&
                                error.getMessage().contains("Payload cannot be null"))
                .verify();
    }

    // Helper methods
    private SiteRequestDto createSiteRequestDto() {
        SiteRequestDto dto = new SiteRequestDto();
        dto.setUserId(UUID.randomUUID());
        dto.setName("Test Site");
        dto.setAddress("123 Test St");
        GeoLocationDto location = new GeoLocationDto();
        location.setLatitude(40.7128);
        location.setLongitude(-74.0060);
        dto.setLocation(location);
        return dto;
    }

    private SiteResponseDto createSiteResponseDto(UUID id) {
        SiteResponseDto dto = new SiteResponseDto();
        dto.setId(id);
        dto.setName("Test Site");
        dto.setAddress("123 Test St");
        GeoLocationDto location = new GeoLocationDto();
        location.setLatitude(40.7128);
        location.setLongitude(-74.0060);
        dto.setLocation(location);
        return dto;
    }

    private SiteReadDocument createSiteReadDocument(UUID id) {
        return SiteReadDocument.builder()
                .id(id.toString())
                .name("Test Site")
                .userId(UUID.randomUUID().toString())
                .address("123 Test St")
                .location(GeoLocationRead.builder()
                        .latitude(40.7128)
                        .longitude(-74.0060)
                        .build())
                .createdAt(Timestamp.now())
                .updatedAt(Timestamp.now())
                .build();
    }

    private ListSitesResponseDto createListSitesResponseDto() {
        ListSitesResponseDto dto = new ListSitesResponseDto();
        dto.setSites(List.of(createSiteResponseDto(UUID.randomUUID())));
        dto.setTotalCount(1);
        dto.setTotalPages(1);
        return dto;
    }
}
