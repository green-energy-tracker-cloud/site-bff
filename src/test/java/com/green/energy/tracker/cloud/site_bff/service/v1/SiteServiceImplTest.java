package com.green.energy.tracker.cloud.site_bff.service.v1;

import com.google.cloud.spring.pubsub.core.publisher.PubSubPublisherTemplate;
import com.google.pubsub.v1.PubsubMessage;
import com.green.energy.tracker.cloud.site.v1.SiteEventType;
import com.green.energy.tracker.cloud.site_bff.model.SiteMapper;
import com.green.energy.tracker.cloud.site_bff.model.SiteReadDocument;
import com.green.energy.tracker.cloud.site_bff.repository.SiteRepository;
import com.green.energy.tracker.cloud.sitebff.web.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private SiteServiceImpl siteService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(siteService, "siteEventsTopic", "site-events");
        ReflectionTestUtils.setField(siteService, "defaultPage", 0);
        ReflectionTestUtils.setField(siteService, "defaultPageSize", 10);
    }

    @Test
    void create_shouldPublishCreateEvent() {
        var siteRequestDto = new SiteRequestDto();
        siteRequestDto.setName("Test Site");
        siteRequestDto.setAddress("123 Test St");
        siteRequestDto.setUserId(UUID.randomUUID());
        var location = new GeoLocationDto();
        location.setLatitude(1.0);
        location.setLongitude(2.0);
        siteRequestDto.setLocation(location);

        when(publisherTemplate.publish(eq("site-events"), any(PubsubMessage.class)))
                .thenReturn(CompletableFuture.completedFuture("messageId"));

        Mono<AsyncOperationResponseDto> result = siteService.create(siteRequestDto);

        StepVerifier.create(result)
                .expectNextMatches(response -> {
                    assertEquals(AsyncOperationResponseDto.StatusEnum.ACCEPTED, response.getStatus());
                    return true;
                })
                .verifyComplete();

        var messageCaptor = ArgumentCaptor.forClass(PubsubMessage.class);
        verify(publisherTemplate).publish(eq("site-events"), messageCaptor.capture());

        var capturedMessage = messageCaptor.getValue();
        assertEquals(SiteEventType.CREATE.name(), capturedMessage.getAttributesMap().get("event_type"));
    }

    @Test
    void delete_shouldPublishDeleteEvent() {
        var id = UUID.randomUUID();

        when(publisherTemplate.publish(eq("site-events"), any(PubsubMessage.class)))
                .thenReturn(CompletableFuture.completedFuture("messageId"));

        Mono<AsyncOperationResponseDto> result = siteService.delete(id);

        StepVerifier.create(result)
                .expectNextMatches(response -> {
                    assertEquals(AsyncOperationResponseDto.StatusEnum.ACCEPTED, response.getStatus());
                    assertEquals(id, response.getId());
                    return true;
                })
                .verifyComplete();

        var messageCaptor = ArgumentCaptor.forClass(PubsubMessage.class);
        verify(publisherTemplate).publish(eq("site-events"), messageCaptor.capture());

        var capturedMessage = messageCaptor.getValue();
        assertEquals(SiteEventType.DELETE.name(), capturedMessage.getAttributesMap().get("event_type"));
        assertEquals(id.toString(), capturedMessage.getAttributesMap().get("entity_id"));
    }

    @Test
    void get_shouldReturnSite_whenFound() {
        var id = UUID.randomUUID();
        var siteResponseDto = new SiteResponseDto();

        when(siteCacheService.getSite(eq(id), any())).thenReturn(Mono.just(siteResponseDto));

        Mono<SiteResponseDto> result = siteService.get(id);

        StepVerifier.create(result)
                .expectNext(siteResponseDto)
                .verifyComplete();

        verify(siteCacheService).getSite(eq(id), any());
    }

    @Test
    void get_shouldReturnNotFound_whenNotFound() {
        var id = UUID.randomUUID();

        when(siteCacheService.getSite(eq(id), any())).thenReturn(
                Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("Site with id %s not found", id)))
        );

        Mono<SiteResponseDto> result = siteService.get(id);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof ResponseStatusException &&
                        ((ResponseStatusException) throwable).getStatusCode() == HttpStatus.NOT_FOUND)
                .verify();
    }

    @Test
    void getAllByUserId_shouldReturnSites() {
        var userId = UUID.randomUUID();
        var page = 0;
        var size = 10;
        var listSitesResponseDto = new ListSitesResponseDto();
        listSitesResponseDto.setTotalCount(1);
        listSitesResponseDto.setTotalPages(1);
        listSitesResponseDto.setSites(java.util.List.of(new SiteResponseDto()));

        when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(size), any()))
                .thenReturn(Mono.just(listSitesResponseDto));

        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

        StepVerifier.create(result)
                .expectNextMatches(response -> {
                    assertEquals(1, response.getTotalCount());
                    assertEquals(1, response.getTotalPages());
                    assertEquals(1, response.getSites().size());
                    return true;
                })
                .verifyComplete();

        verify(siteCacheService).getSitesByUserId(eq(userId), eq(page), eq(size), any());
    }

    @Test
    void getAllByUserId_shouldUseDefaultPage_whenPageIsNull() {
        var userId = UUID.randomUUID();
        var listSitesResponseDto = new ListSitesResponseDto();
        listSitesResponseDto.setTotalCount(0);
        listSitesResponseDto.setTotalPages(0);
        listSitesResponseDto.setSites(java.util.List.of());

        when(siteCacheService.getSitesByUserId(eq(userId), eq(0), eq(10), any()))
                .thenReturn(Mono.just(listSitesResponseDto));

        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, null, null);

        StepVerifier.create(result)
                .expectNext(listSitesResponseDto)
                .verifyComplete();

        verify(siteCacheService).getSitesByUserId(eq(userId), eq(0), eq(10), any());
    }

    @Test
    void getAllByUserId_shouldUseDefaultSize_whenSizeIsNull() {
        var userId = UUID.randomUUID();
        var page = 1;
        var listSitesResponseDto = new ListSitesResponseDto();

        when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(10), any()))
                .thenReturn(Mono.just(listSitesResponseDto));

        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, null);

        StepVerifier.create(result)
                .expectNext(listSitesResponseDto)
                .verifyComplete();

        verify(siteCacheService).getSitesByUserId(eq(userId), eq(page), eq(10), any());
    }

    @Test
    void patch_shouldPublishPatchEvent() {
        var id = UUID.randomUUID();
        var siteRequestDto = new SiteRequestDto();
        siteRequestDto.setName("Updated Site");
        siteRequestDto.setAddress("456 Updated St");
        siteRequestDto.setUserId(UUID.randomUUID());
        var location = new GeoLocationDto();
        location.setLatitude(3.0);
        location.setLongitude(4.0);
        siteRequestDto.setLocation(location);

        when(publisherTemplate.publish(eq("site-events"), any(PubsubMessage.class)))
                .thenReturn(CompletableFuture.completedFuture("messageId"));

        Mono<AsyncOperationResponseDto> result = siteService.patch(id, siteRequestDto);

        StepVerifier.create(result)
                .expectNextMatches(response -> {
                    assertEquals(AsyncOperationResponseDto.StatusEnum.ACCEPTED, response.getStatus());
                    return true;
                })
                .verifyComplete();

        var messageCaptor = ArgumentCaptor.forClass(PubsubMessage.class);
        verify(publisherTemplate).publish(eq("site-events"), messageCaptor.capture());

        var capturedMessage = messageCaptor.getValue();
        assertEquals(SiteEventType.PATCH.name(), capturedMessage.getAttributesMap().get("event_type"));
    }

    @Test
    void update_shouldPublishUpdateEvent() {
        var id = UUID.randomUUID();
        var siteRequestDto = new SiteRequestDto();
        siteRequestDto.setName("Updated Site");
        siteRequestDto.setAddress("456 Updated St");
        siteRequestDto.setUserId(UUID.randomUUID());
        var location = new GeoLocationDto();
        location.setLatitude(3.0);
        location.setLongitude(4.0);
        siteRequestDto.setLocation(location);

        when(publisherTemplate.publish(eq("site-events"), any(PubsubMessage.class)))
                .thenReturn(CompletableFuture.completedFuture("messageId"));

        Mono<AsyncOperationResponseDto> result = siteService.update(id, siteRequestDto);

        StepVerifier.create(result)
                .expectNextMatches(response -> {
                    assertEquals(AsyncOperationResponseDto.StatusEnum.ACCEPTED, response.getStatus());
                    return true;
                })
                .verifyComplete();

        var messageCaptor = ArgumentCaptor.forClass(PubsubMessage.class);
        verify(publisherTemplate).publish(eq("site-events"), messageCaptor.capture());

        var capturedMessage = messageCaptor.getValue();
        assertEquals(SiteEventType.UPDATE.name(), capturedMessage.getAttributesMap().get("event_type"));
    }

    // Tests for getSiteFromDb (private method tested indirectly via cache fallback)
    @Test
    void getSiteFromDb_shouldReturnMappedSite_whenSiteExists() {
        var id = UUID.randomUUID();
        var siteDocument = new SiteReadDocument();
        siteDocument.setId(id.toString());
        siteDocument.setName("Test Site");
        var siteResponseDto = new SiteResponseDto();
        siteResponseDto.setId(id);
        siteResponseDto.setName("Test Site");

        // Capture the supplier passed to cache service
        when(siteCacheService.getSite(eq(id), any())).thenAnswer((Answer<Mono<SiteResponseDto>>) invocation -> {
            Supplier<Mono<SiteResponseDto>> supplier = invocation.getArgument(1);
            // Mock the repository and mapper for the fallback
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
    void getSiteFromDb_shouldThrowNotFound_whenSiteDoesNotExist() {
        var id = UUID.randomUUID();

        // Capture the supplier passed to cache service
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

    // Tests for getSitesByUserIdFromDb (private method tested indirectly via cache fallback)
    @Test
    void getSitesByUserIdFromDb_shouldReturnListWithCorrectPagination() {
        var userId = UUID.randomUUID();
        var page = 0;
        var size = 10;
        var pageable = PageRequest.of(page, size);

        var siteDocument1 = new SiteReadDocument();
        siteDocument1.setId(UUID.randomUUID().toString());
        siteDocument1.setUserId(userId.toString());

        var siteDocument2 = new SiteReadDocument();
        siteDocument2.setId(UUID.randomUUID().toString());
        siteDocument2.setUserId(userId.toString());

        var siteDto1 = new SiteResponseDto();
        siteDto1.setId(UUID.fromString(siteDocument1.getId()));

        var siteDto2 = new SiteResponseDto();
        siteDto2.setId(UUID.fromString(siteDocument2.getId()));

        when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(size), any()))
                .thenAnswer((Answer<Mono<ListSitesResponseDto>>) invocation -> {
                    Supplier<Mono<ListSitesResponseDto>> supplier = invocation.getArgument(3);
                    when(siteRepository.countByUserId(userId.toString())).thenReturn(Mono.just(2L));
                    when(siteRepository.findAllByUserId(userId.toString(), pageable))
                            .thenReturn(Flux.just(siteDocument1, siteDocument2));
                    when(siteMapper.toDto(siteDocument1)).thenReturn(siteDto1);
                    when(siteMapper.toDto(siteDocument2)).thenReturn(siteDto2);
                    return supplier.get();
                });

        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

        StepVerifier.create(result)
                .expectNextMatches(response -> {
                    assertEquals(2, response.getTotalCount());
                    assertEquals(1, response.getTotalPages());
                    assertEquals(2, response.getSites().size());
                    return true;
                })
                .verifyComplete();

        verify(siteRepository).countByUserId(userId.toString());
        verify(siteRepository).findAllByUserId(userId.toString(), pageable);
        verify(siteMapper, times(2)).toDto(any(SiteReadDocument.class));
    }

    @Test
    void getSitesByUserIdFromDb_shouldCalculateCorrectTotalPages() {
        var userId = UUID.randomUUID();
        var page = 0;
        var size = 10;
        var totalElements = 25L;
        var pageable = PageRequest.of(page, size);

        var sites = List.of(new SiteReadDocument(), new SiteReadDocument());

        when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(size), any()))
                .thenAnswer((Answer<Mono<ListSitesResponseDto>>) invocation -> {
                    Supplier<Mono<ListSitesResponseDto>> supplier = invocation.getArgument(3);
                    when(siteRepository.countByUserId(userId.toString())).thenReturn(Mono.just(totalElements));
                    when(siteRepository.findAllByUserId(userId.toString(), pageable))
                            .thenReturn(Flux.fromIterable(sites));
                    when(siteMapper.toDto(any(SiteReadDocument.class))).thenReturn(new SiteResponseDto());
                    return supplier.get();
                });

        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

        StepVerifier.create(result)
                .expectNextMatches(response -> {
                    assertEquals(25, response.getTotalCount());
                    assertEquals(3, response.getTotalPages()); // Math.ceil(25/10) = 3
                    return true;
                })
                .verifyComplete();
    }

    @Test
    void getSitesByUserIdFromDb_shouldReturnEmptyList_whenNoSitesFound() {
        var userId = UUID.randomUUID();
        var page = 0;
        var size = 10;
        var pageable = PageRequest.of(page, size);

        when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(size), any()))
                .thenAnswer((Answer<Mono<ListSitesResponseDto>>) invocation -> {
                    Supplier<Mono<ListSitesResponseDto>> supplier = invocation.getArgument(3);
                    when(siteRepository.countByUserId(userId.toString())).thenReturn(Mono.just(0L));
                    when(siteRepository.findAllByUserId(userId.toString(), pageable))
                            .thenReturn(Flux.empty());
                    return supplier.get();
                });

        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

        StepVerifier.create(result)
                .expectNextMatches(response -> {
                    assertEquals(0, response.getTotalCount());
                    assertEquals(0, response.getTotalPages());
                    assertEquals(0, response.getSites().size());
                    return true;
                })
                .verifyComplete();

        verify(siteRepository).countByUserId(userId.toString());
        verify(siteRepository).findAllByUserId(userId.toString(), pageable);
    }

    @Test
    void getSitesByUserIdFromDb_shouldHandleSinglePage() {
        var userId = UUID.randomUUID();
        var page = 0;
        var size = 10;
        var totalElements = 5L;
        var pageable = PageRequest.of(page, size);

        var sites = List.of(
                new SiteReadDocument(),
                new SiteReadDocument(),
                new SiteReadDocument(),
                new SiteReadDocument(),
                new SiteReadDocument()
        );

        when(siteCacheService.getSitesByUserId(eq(userId), eq(page), eq(size), any()))
                .thenAnswer((Answer<Mono<ListSitesResponseDto>>) invocation -> {
                    Supplier<Mono<ListSitesResponseDto>> supplier = invocation.getArgument(3);
                    when(siteRepository.countByUserId(userId.toString())).thenReturn(Mono.just(totalElements));
                    when(siteRepository.findAllByUserId(userId.toString(), pageable))
                            .thenReturn(Flux.fromIterable(sites));
                    when(siteMapper.toDto(any(SiteReadDocument.class))).thenReturn(new SiteResponseDto());
                    return supplier.get();
                });

        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

        StepVerifier.create(result)
                .expectNextMatches(response -> {
                    assertEquals(5, response.getTotalCount());
                    assertEquals(1, response.getTotalPages()); // Math.ceil(5/10) = 1
                    assertEquals(5, response.getSites().size());
                    return true;
                })
                .verifyComplete();
    }
}
