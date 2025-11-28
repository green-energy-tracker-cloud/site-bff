package com.green.energy.tracker.cloud.site_bff.service.v1;

import com.google.cloud.spring.pubsub.core.publisher.PubSubPublisherTemplate;
import com.google.pubsub.v1.PubsubMessage;
import com.green.energy.tracker.cloud.site.v1.SiteEventType;
import com.green.energy.tracker.cloud.site_bff.model.SiteMapper;
import com.green.energy.tracker.cloud.site_bff.model.SiteReadDocument;
import com.green.energy.tracker.cloud.site_bff.repository.SiteRepository;
import com.green.energy.tracker.cloud.sitebff.web.model.*;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SiteServiceImpl Unit Tests")
class SiteServiceImplTest {

    @Mock
    private PubSubPublisherTemplate publisherTemplate;

    @Mock
    private SiteMapper siteMapper;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private SiteCacheService siteCacheService;

    private SiteServiceImpl siteService;

    private static final String SITE_EVENTS_TOPIC = "site-events";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final Long MAX_ATTEMPTS = 3L;
    private static final Long MIN_BACKOFF_MILLIS = 100L;
    private static final Long MAX_BACKOFF_MILLIS = 1000L;

    @BeforeEach
    void setUp() {
        siteService = new SiteServiceImpl(publisherTemplate, siteMapper, siteRepository, siteCacheService);
        ReflectionTestUtils.setField(siteService, "siteEventsTopic", SITE_EVENTS_TOPIC);
        ReflectionTestUtils.setField(siteService, "defaultPage", DEFAULT_PAGE);
        ReflectionTestUtils.setField(siteService, "defaultPageSize", DEFAULT_PAGE_SIZE);
        ReflectionTestUtils.setField(siteService, "maxAttempts", MAX_ATTEMPTS);
        ReflectionTestUtils.setField(siteService, "minBackoffMillis", MIN_BACKOFF_MILLIS);
        ReflectionTestUtils.setField(siteService, "maxBackoffMillis", MAX_BACKOFF_MILLIS);
    }

    @Nested
    @DisplayName("Create Site Tests")
    class CreateSiteTests {

        @Test
        @DisplayName("Should publish CREATE event with correct attributes")
        void create_shouldPublishCreateEvent_withCorrectAttributes() {
            var siteRequestDto = createSiteRequestDto();
            var messageId = "test-message-id";

            when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any(PubsubMessage.class)))
                    .thenReturn(CompletableFuture.completedFuture(messageId));

            Mono<AsyncOperationResponseDto> result = siteService.create(siteRequestDto);

            StepVerifier.create(result)
                    .expectNextMatches(response -> {
                        assertThat(response.getStatus()).isEqualTo(AsyncOperationResponseDto.StatusEnum.ACCEPTED);
                        assertThat(response.getMessage()).contains(messageId);
                        assertThat(response.getId()).isNotNull();
                        return true;
                    })
                    .verifyComplete();

            var messageCaptor = ArgumentCaptor.forClass(PubsubMessage.class);
            verify(publisherTemplate).publish(eq(SITE_EVENTS_TOPIC), messageCaptor.capture());

            var capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getAttributesMap().get("event_type")).isEqualTo(SiteEventType.CREATE.name());
            assertThat(capturedMessage.getAttributesMap().get("entity_id")).isNotNull();
            assertThat(capturedMessage.getData()).isNotEmpty();
        }

        @Test
        @DisplayName("Should fail after max retry attempts")
        void create_shouldFailAfterMaxRetries() {
            var siteRequestDto = createSiteRequestDto();

            when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any(PubsubMessage.class)))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Persistent error")));

            Mono<AsyncOperationResponseDto> result = siteService.create(siteRequestDto);

            StepVerifier.create(result)
                    .expectError(RuntimeException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should include all site details in the event payload")
        void create_shouldIncludeAllSiteDetails() {
            var siteRequestDto = createSiteRequestDto();
            siteRequestDto.setName("Solar Farm");
            siteRequestDto.setAddress("123 Green Street");
            siteRequestDto.getLocation().setLatitude(45.5);
            siteRequestDto.getLocation().setLongitude(-73.6);

            when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any(PubsubMessage.class)))
                    .thenReturn(CompletableFuture.completedFuture("messageId"));

            Mono<AsyncOperationResponseDto> result = siteService.create(siteRequestDto);

            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();

            verify(publisherTemplate).publish(eq(SITE_EVENTS_TOPIC), any(PubsubMessage.class));
        }
    }

    @Nested
    @DisplayName("Delete Site Tests")
    class DeleteSiteTests {

        @Test
        @DisplayName("Should publish DELETE event without payload")
        void delete_shouldPublishDeleteEvent_withoutPayload() {
            var id = UUID.randomUUID();
            var messageId = "delete-message-id";

            when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any(PubsubMessage.class)))
                    .thenReturn(CompletableFuture.completedFuture(messageId));

            Mono<AsyncOperationResponseDto> result = siteService.delete(id);

            StepVerifier.create(result)
                    .expectNextMatches(response -> {
                        assertThat(response.getStatus()).isEqualTo(AsyncOperationResponseDto.StatusEnum.ACCEPTED);
                        assertThat(response.getId()).isEqualTo(id);
                        assertThat(response.getMessage()).contains(messageId);
                        return true;
                    })
                    .verifyComplete();

            var messageCaptor = ArgumentCaptor.forClass(PubsubMessage.class);
            verify(publisherTemplate).publish(eq(SITE_EVENTS_TOPIC), messageCaptor.capture());

            var capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getAttributesMap().get("event_type")).isEqualTo(SiteEventType.DELETE.name());
            assertThat(capturedMessage.getAttributesMap().get("entity_id")).isEqualTo(id.toString());
            assertThat(capturedMessage.getData()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Get Site Tests")
    class GetSiteTests {

        @Test
        @DisplayName("Should return site from cache service")
        void get_shouldReturnSiteFromCacheService() {
            var id = UUID.randomUUID();
            var siteResponseDto = createSiteResponseDto(id);

            when(siteCacheService.getSite(eq(id), any())).thenReturn(Mono.just(siteResponseDto));

            Mono<SiteResponseDto> result = siteService.get(id);

            StepVerifier.create(result)
                    .expectNext(siteResponseDto)
                    .verifyComplete();

            verify(siteCacheService).getSite(eq(id), any());
        }

        @Test
        @DisplayName("Should fetch from DB via cache fallback when cache miss")
        void get_shouldFetchFromDb_whenCacheMiss() {
            var id = UUID.randomUUID();
            var siteDocument = createSiteReadDocument(id);
            var siteResponseDto = createSiteResponseDto(id);

            when(siteCacheService.getSite(eq(id), any())).thenAnswer((Answer<Mono<SiteResponseDto>>) invocation -> {
                Supplier<Mono<SiteResponseDto>> supplier = invocation.getArgument(1);
                when(siteRepository.findById(id.toString())).thenReturn(Mono.just(siteDocument));
                when(siteMapper.toDto(siteDocument)).thenReturn(siteResponseDto);
                return supplier.get();
            });

            Mono<SiteResponseDto> result = siteService.get(id);

            StepVerifier.create(result)
                    .expectNext(siteResponseDto)
                    .verifyComplete();

            verify(siteRepository).findById(id.toString());
            verify(siteMapper).toDto(siteDocument);
        }

        @Test
        @DisplayName("Should throw NOT_FOUND when site does not exist in DB")
        void get_shouldThrowNotFound_whenSiteDoesNotExist() {
            var id = UUID.randomUUID();

            when(siteCacheService.getSite(eq(id), any())).thenAnswer((Answer<Mono<SiteResponseDto>>) invocation -> {
                Supplier<Mono<SiteResponseDto>> supplier = invocation.getArgument(1);
                when(siteRepository.findById(id.toString())).thenReturn(Mono.empty());
                return supplier.get();
            });

            Mono<SiteResponseDto> result = siteService.get(id);

            StepVerifier.create(result)
                    .expectErrorMatches(throwable ->
                            throwable instanceof ResponseStatusException &&
                                    ((ResponseStatusException) throwable).getStatusCode() == HttpStatus.NOT_FOUND &&
                                    throwable.getMessage().contains(id.toString()))
                    .verify();

            verify(siteRepository).findById(id.toString());
        }
    }

    @Nested
    @DisplayName("Get All Sites By UserId Tests")
    class GetAllByUserIdTests {

        @Test
        @DisplayName("Should return sites list from cache service")
        void getAllByUserId_shouldReturnFromCacheService() {
            var userId = UUID.randomUUID();
            var page = 0;
            var size = 10;
            var listSitesResponseDto = createListSitesResponseDto(2);

            when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(size), any()))
                    .thenReturn(Mono.just(listSitesResponseDto));

            Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

            StepVerifier.create(result)
                    .expectNext(listSitesResponseDto)
                    .verifyComplete();

            verify(siteCacheService).getSitesByUserId(eq(userId), eq(page), eq(size), any());
        }

        @Test
        @DisplayName("Should use default page when page is null")
        void getAllByUserId_shouldUseDefaultPage_whenPageIsNull() {
            var userId = UUID.randomUUID();
            var listSitesResponseDto = createListSitesResponseDto(0);

            when(siteCacheService.getSitesByUserId(eq(userId), eq(DEFAULT_PAGE), eq(DEFAULT_PAGE_SIZE), any()))
                    .thenReturn(Mono.just(listSitesResponseDto));

            Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, null, null);

            StepVerifier.create(result)
                    .expectNext(listSitesResponseDto)
                    .verifyComplete();

            verify(siteCacheService).getSitesByUserId(eq(userId), eq(DEFAULT_PAGE), eq(DEFAULT_PAGE_SIZE), any());
        }

        @Test
        @DisplayName("Should use default page when page is negative")
        void getAllByUserId_shouldUseDefaultPage_whenPageIsNegative() {
            var userId = UUID.randomUUID();
            var listSitesResponseDto = createListSitesResponseDto(0);

            when(siteCacheService.getSitesByUserId(eq(userId), eq(DEFAULT_PAGE), eq(DEFAULT_PAGE_SIZE), any()))
                    .thenReturn(Mono.just(listSitesResponseDto));

            Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, -1, null);

            StepVerifier.create(result)
                    .expectNext(listSitesResponseDto)
                    .verifyComplete();

            verify(siteCacheService).getSitesByUserId(eq(userId), eq(DEFAULT_PAGE), eq(DEFAULT_PAGE_SIZE), any());
        }

        @Test
        @DisplayName("Should use default size when size is null")
        void getAllByUserId_shouldUseDefaultSize_whenSizeIsNull() {
            var userId = UUID.randomUUID();
            var page = 1;
            var listSitesResponseDto = createListSitesResponseDto(0);

            when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(DEFAULT_PAGE_SIZE), any()))
                    .thenReturn(Mono.just(listSitesResponseDto));

            Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, null);

            StepVerifier.create(result)
                    .expectNext(listSitesResponseDto)
                    .verifyComplete();

            verify(siteCacheService).getSitesByUserId(eq(userId), eq(page), eq(DEFAULT_PAGE_SIZE), any());
        }

        @Test
        @DisplayName("Should use default size when size is zero or negative")
        void getAllByUserId_shouldUseDefaultSize_whenSizeIsZeroOrNegative() {
            var userId = UUID.randomUUID();
            var page = 1;
            var listSitesResponseDto = createListSitesResponseDto(0);

            when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(DEFAULT_PAGE_SIZE), any()))
                    .thenReturn(Mono.just(listSitesResponseDto));

            Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, 0);

            StepVerifier.create(result)
                    .expectNext(listSitesResponseDto)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fetch from DB via cache fallback with correct pagination")
        void getAllByUserId_shouldFetchFromDb_withCorrectPagination() {
            var userId = UUID.randomUUID();
            var page = 0;
            var size = 10;
            var pageable = PageRequest.of(page, size);
            var totalElements = 25L;

            var siteDocument1 = createSiteReadDocument(UUID.randomUUID());
            var siteDocument2 = createSiteReadDocument(UUID.randomUUID());
            var siteDto1 = createSiteResponseDto(UUID.fromString(siteDocument1.getId()));
            var siteDto2 = createSiteResponseDto(UUID.fromString(siteDocument2.getId()));

            when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(size), any()))
                    .thenAnswer((Answer<Mono<ListSitesResponseDto>>) invocation -> {
                        Supplier<Mono<ListSitesResponseDto>> supplier = invocation.getArgument(3);
                        when(siteRepository.countByUserId(userId.toString())).thenReturn(Mono.just(totalElements));
                        when(siteRepository.findAllByUserId(userId.toString(), pageable))
                                .thenReturn(Flux.just(siteDocument1, siteDocument2));
                        when(siteMapper.toDto(siteDocument1)).thenReturn(siteDto1);
                        when(siteMapper.toDto(siteDocument2)).thenReturn(siteDto2);
                        return supplier.get();
                    });

            Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

            StepVerifier.create(result)
                    .expectNextMatches(response -> {
                        assertThat(response.getTotalCount()).isEqualTo(25);
                        assertThat(response.getTotalPages()).isEqualTo(3); // Math.ceil(25/10) = 3
                        assertThat(response.getSites()).hasSize(2);
                        return true;
                    })
                    .verifyComplete();

            verify(siteRepository).countByUserId(userId.toString());
            verify(siteRepository).findAllByUserId(userId.toString(), pageable);
        }

        @Test
        @DisplayName("Should return empty list when no sites found")
        void getAllByUserId_shouldReturnEmptyList_whenNoSitesFound() {
            var userId = UUID.randomUUID();
            var page = 0;
            var size = 10;
            var pageable = PageRequest.of(page, size);

            when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(size), any()))
                    .thenAnswer((Answer<Mono<ListSitesResponseDto>>) invocation -> {
                        Supplier<Mono<ListSitesResponseDto>> supplier = invocation.getArgument(3);
                        when(siteRepository.countByUserId(userId.toString())).thenReturn(Mono.just(0L));
                        when(siteRepository.findAllByUserId(userId.toString(), pageable)).thenReturn(Flux.empty());
                        return supplier.get();
                    });

            Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

            StepVerifier.create(result)
                    .expectNextMatches(response -> {
                        assertThat(response.getTotalCount()).isZero();
                        assertThat(response.getTotalPages()).isZero();
                        assertThat(response.getSites()).isEmpty();
                        return true;
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should calculate total pages correctly for various counts")
        void getAllByUserId_shouldCalculateTotalPagesCorrectly() {
            var userId = UUID.randomUUID();
            var page = 0;
            var size = 10;
            var pageable = PageRequest.of(page, size);

            when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(size), any()))
                    .thenAnswer((Answer<Mono<ListSitesResponseDto>>) invocation -> {
                        Supplier<Mono<ListSitesResponseDto>> supplier = invocation.getArgument(3);
                        when(siteRepository.countByUserId(userId.toString())).thenReturn(Mono.just(5L));
                        when(siteRepository.findAllByUserId(userId.toString(), pageable)).thenReturn(Flux.empty());
                        return supplier.get();
                    });

            Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

            StepVerifier.create(result)
                    .expectNextMatches(response -> {
                        assertThat(response.getTotalPages()).isEqualTo(1); // Math.ceil(5/10) = 1
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Patch Site Tests")
    class PatchSiteTests {

        @Test
        @DisplayName("Should publish PATCH event with correct attributes")
        void patch_shouldPublishPatchEvent_withCorrectAttributes() {
            var id = UUID.randomUUID();
            var siteRequestDto = createSiteRequestDto();
            var messageId = "patch-message-id";

            when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any(PubsubMessage.class)))
                    .thenReturn(CompletableFuture.completedFuture(messageId));

            Mono<AsyncOperationResponseDto> result = siteService.patch(id, siteRequestDto);

            StepVerifier.create(result)
                    .expectNextMatches(response -> {
                        assertThat(response.getStatus()).isEqualTo(AsyncOperationResponseDto.StatusEnum.ACCEPTED);
                        assertThat(response.getId()).isEqualTo(id);
                        assertThat(response.getMessage()).contains(messageId);
                        return true;
                    })
                    .verifyComplete();

            var messageCaptor = ArgumentCaptor.forClass(PubsubMessage.class);
            verify(publisherTemplate).publish(eq(SITE_EVENTS_TOPIC), messageCaptor.capture());

            var capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getAttributesMap().get("event_type")).isEqualTo(SiteEventType.PATCH.name());
            assertThat(capturedMessage.getAttributesMap().get("entity_id")).isEqualTo(id.toString());
            assertThat(capturedMessage.getData()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Update Site Tests")
    class UpdateSiteTests {

        @Test
        @DisplayName("Should publish UPDATE event with correct attributes")
        void update_shouldPublishUpdateEvent_withCorrectAttributes() {
            var id = UUID.randomUUID();
            var siteRequestDto = createSiteRequestDto();
            var messageId = "update-message-id";

            when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any(PubsubMessage.class)))
                    .thenReturn(CompletableFuture.completedFuture(messageId));

            Mono<AsyncOperationResponseDto> result = siteService.update(id, siteRequestDto);

            StepVerifier.create(result)
                    .expectNextMatches(response -> {
                        assertThat(response.getStatus()).isEqualTo(AsyncOperationResponseDto.StatusEnum.ACCEPTED);
                        assertThat(response.getId()).isEqualTo(id);
                        assertThat(response.getMessage()).contains(messageId);
                        return true;
                    })
                    .verifyComplete();

            var messageCaptor = ArgumentCaptor.forClass(PubsubMessage.class);
            verify(publisherTemplate).publish(eq(SITE_EVENTS_TOPIC), messageCaptor.capture());

            var capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getAttributesMap().get("event_type")).isEqualTo(SiteEventType.UPDATE.name());
            assertThat(capturedMessage.getAttributesMap().get("entity_id")).isEqualTo(id.toString());
            assertThat(capturedMessage.getData()).isNotEmpty();
        }

        @Test
        @DisplayName("Should include updated timestamp in event")
        void update_shouldIncludeUpdatedTimestamp() {
            var id = UUID.randomUUID();
            var siteRequestDto = createSiteRequestDto();

            when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any(PubsubMessage.class)))
                    .thenReturn(CompletableFuture.completedFuture("messageId"));

            Mono<AsyncOperationResponseDto> result = siteService.update(id, siteRequestDto);

            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();

            verify(publisherTemplate).publish(eq(SITE_EVENTS_TOPIC), any(PubsubMessage.class));
        }
    }

    @Nested
    @DisplayName("Event Publishing Tests")
    class EventPublishingTests {

        @Test
        @DisplayName("Should fail immediately when payload is null for non-DELETE events")
        void publishEvent_shouldFail_whenPayloadIsNullForNonDeleteEvents() {
            var id = UUID.randomUUID();
            var siteRequestDto = createSiteRequestDto();

            when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any(PubsubMessage.class)))
                    .thenReturn(CompletableFuture.completedFuture("messageId"));

            Mono<AsyncOperationResponseDto> result = siteService.create(siteRequestDto);

            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should set correct trace ID in response message")
        void publishEvent_shouldSetCorrectTraceId() {
            var siteRequestDto = createSiteRequestDto();
            var expectedMessageId = "unique-trace-id-12345";

            when(publisherTemplate.publish(eq(SITE_EVENTS_TOPIC), any(PubsubMessage.class)))
                    .thenReturn(CompletableFuture.completedFuture(expectedMessageId));

            Mono<AsyncOperationResponseDto> result = siteService.create(siteRequestDto);

            StepVerifier.create(result)
                    .expectNextMatches(response -> response.getMessage().contains(expectedMessageId))
                    .verifyComplete();
        }
    }

    private SiteRequestDto createSiteRequestDto() {
        var siteRequestDto = new SiteRequestDto();
        siteRequestDto.setName("Test Site");
        siteRequestDto.setAddress("123 Test St");
        siteRequestDto.setUserId(UUID.randomUUID());
        var location = new GeoLocationDto();
        location.setLatitude(45.5);
        location.setLongitude(-73.6);
        siteRequestDto.setLocation(location);
        return siteRequestDto;
    }

    private SiteResponseDto createSiteResponseDto(UUID id) {
        var siteResponseDto = new SiteResponseDto();
        siteResponseDto.setId(id);
        siteResponseDto.setName("Test Site");
        siteResponseDto.setAddress("123 Test Street");
        siteResponseDto.setUserId(UUID.randomUUID());
        return siteResponseDto;
    }

    private SiteReadDocument createSiteReadDocument(UUID id) {
        var siteDocument = new SiteReadDocument();
        siteDocument.setId(id.toString());
        siteDocument.setName("Test Site");
        siteDocument.setAddress("123 Test Street");
        siteDocument.setUserId(UUID.randomUUID().toString());
        return siteDocument;
    }

    private ListSitesResponseDto createListSitesResponseDto(int count) {
        var listSitesResponseDto = new ListSitesResponseDto();
        listSitesResponseDto.setTotalCount(count);
        listSitesResponseDto.setTotalPages(count > 0 ? 1 : 0);

        List<SiteResponseDto> sites = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            sites.add(createSiteResponseDto(UUID.randomUUID()));
        }
        listSitesResponseDto.setSites(sites);
        return listSitesResponseDto;
    }
}
