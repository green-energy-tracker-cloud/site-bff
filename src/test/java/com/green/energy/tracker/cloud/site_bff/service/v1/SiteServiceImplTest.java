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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteServiceImplTest {

    @Mock
    private PubSubPublisherTemplate publisherTemplate;

    @Mock
    private SiteMapper siteMapper;

    @Mock
    private SiteRepository siteRepository;

    @InjectMocks
    private SiteServiceImpl siteService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(siteService, "siteEventsTopic", "site-events");
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
        var siteDocument = new SiteReadDocument();
        var siteResponseDto = new SiteResponseDto();

        when(siteRepository.findById(id.toString())).thenReturn(Mono.just(siteDocument));
        when(siteMapper.toDto(siteDocument)).thenReturn(siteResponseDto);

        Mono<SiteResponseDto> result = siteService.get(id);

        StepVerifier.create(result)
                .expectNext(siteResponseDto)
                .verifyComplete();
    }

    @Test
    void get_shouldReturnNotFound_whenNotFound() {
        var id = UUID.randomUUID();

        when(siteRepository.findById(id.toString())).thenReturn(Mono.empty());

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
        var pageable = PageRequest.of(page, size);
        var siteDocument = new SiteReadDocument();
        var siteResponseDto = new SiteResponseDto();

        when(siteRepository.countByUserId(userId.toString())).thenReturn(Mono.just(1L));
        when(siteRepository.findAllByUserId(userId.toString(), pageable)).thenReturn(Flux.just(siteDocument));
        when(siteMapper.toDto(siteDocument)).thenReturn(siteResponseDto);

        Mono<ListSitesResponseDto> result = siteService.getAllByUserId(userId, page, size);

        StepVerifier.create(result)
                .expectNextMatches(response -> {
                    assertEquals(1, response.getTotalCount());
                    assertEquals(1, response.getTotalPages());
                    assertEquals(1, response.getSites().size());
                    return true;
                })
                .verifyComplete();
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
}
