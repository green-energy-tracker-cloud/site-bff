package com.green.energy.tracker.cloud.site_bff.service;

import com.google.cloud.spring.pubsub.core.publisher.PubSubPublisherTemplate;
import com.google.pubsub.v1.PubsubMessage;
import com.green.energy.tracker.cloud.common.v1.GeoLocation;
import com.green.energy.tracker.cloud.site.v1.Site;
import com.green.energy.tracker.cloud.site.v1.SiteEventType;
import com.green.energy.tracker.cloud.site_bff.model.SiteMapper;
import com.green.energy.tracker.cloud.site_bff.repository.SiteRepository;
import com.green.energy.tracker.cloud.sitebff.web.model.AsyncOperationResponseDto;
import com.green.energy.tracker.cloud.sitebff.web.model.ListSitesResponseDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteRequestDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteResponseDto;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class SiteServiceImpl implements SiteService {
    @Value("${spring.cloud.gcp.pubsub.topic.site-events}")
    private String siteEventsTopic;
    @Value("${pagination.default.page:0}")
    private int defaultPage;
    @Value("${pagination.default.size:10}")
    private int defaultPageSize;

    private static final String CB_FIRESTORE_ID = "firestore";
    private static final String CB_PUBSUB_ID = "pubsub";

    private final PubSubPublisherTemplate publisherTemplate;
    private final SiteMapper siteMapper;
    private final SiteRepository siteRepository;
    private final SiteCacheService siteCacheService;
    private final ReactiveCircuitBreaker cbPubSub;
    private final ReactiveCircuitBreaker cbFirestore;
    private final Retry retryPubSub;
    private final Retry retryFirestore;

    public SiteServiceImpl(PubSubPublisherTemplate publisherTemplate,
                           SiteMapper siteMapper,
                           SiteRepository siteRepository,
                           SiteCacheService siteCacheService,
                           @Qualifier("cbPubSub") ReactiveCircuitBreaker cbPubSub,
                           @Qualifier("cbFirestore") ReactiveCircuitBreaker cbFirestore,
                           @Qualifier("retryPubSub") Retry retryPubSub,
                           @Qualifier("retryFirestore") Retry retryFirestore) {
        this.publisherTemplate = publisherTemplate;
        this.siteMapper = siteMapper;
        this.siteRepository = siteRepository;
        this.siteCacheService = siteCacheService;
        this.cbPubSub = cbPubSub;
        this.cbFirestore = cbFirestore;
        this.retryPubSub = retryPubSub;
        this.retryFirestore = retryFirestore;

        this.retryPubSub.getEventPublisher().onRetry(event -> log.warn("Retrying PubSub. Attempt #{} due to: {}",
                event.getNumberOfRetryAttempts(), Objects.nonNull(event.getLastThrowable()) ? event.getLastThrowable().getMessage() : ""));

        this.retryFirestore.getEventPublisher().onRetry(event -> log.warn("Retrying Firestore. Attempt #{} due to: {}",
                event.getNumberOfRetryAttempts(), Objects.nonNull(event.getLastThrowable()) ? event.getLastThrowable().getMessage() : ""));
    }

    @Override
    public Mono<AsyncOperationResponseDto> create(SiteRequestDto siteRequestDto) {
        var id = UUID.randomUUID().toString();
        var site = buildSiteFromRequest(id, siteRequestDto)
                .setCreatedAt(Instant.now().toString())
                .setUpdatedAt(Instant.now().toString())
                .build();
        return publishEvent(SiteEventType.CREATE, id, site);
    }

    @Override
    public Mono<AsyncOperationResponseDto> delete(UUID id) {
        return publishEvent(SiteEventType.DELETE, id.toString(), null);
    }

    @Override
    public Mono<SiteResponseDto> get(UUID id) {
        return siteCacheService.getSite(id, () -> getSiteFromDb(id));
    }

    @Override
    public Mono<ListSitesResponseDto> getAllByUserId(UUID userId, Integer page, Integer size) {
        int pageNum = (page != null && page >= 0) ? page : defaultPage;
        int pageSize = (size != null && size > 0) ? size : defaultPageSize;
        return siteCacheService.getSitesByUserId(userId, pageNum, pageSize, () -> getSitesByUserIdFromDb(userId, pageNum, pageSize));
    }

    @Override
    public Mono<AsyncOperationResponseDto> patch(UUID id, SiteRequestDto siteRequestDto) {
        return handleSiteUpdate(id, siteRequestDto, SiteEventType.PATCH);
    }

    @Override
    public Mono<AsyncOperationResponseDto> update(UUID id, SiteRequestDto siteRequestDto) {
        return handleSiteUpdate(id, siteRequestDto, SiteEventType.UPDATE);
    }

    private Mono<AsyncOperationResponseDto> handleSiteUpdate(UUID id, SiteRequestDto siteRequestDto, SiteEventType eventType) {
        var site = buildSiteFromRequest(id.toString(), siteRequestDto)
                .setUpdatedAt(Instant.now().toString())
                .build();
        return publishEvent(eventType, id.toString(), site);
    }

    private GeoLocation buildGeoLocationFromRequest(double latitude, double longitude) {
        return GeoLocation.newBuilder()
                .setLatitude(latitude)
                .setLongitude(longitude)
                .build();
    }

    private Site.Builder buildSiteFromRequest(String id, SiteRequestDto request) {
        return Site.newBuilder()
                .setId(id)
                .setUserId(request.getUserId().toString())
                .setName(request.getName())
                .setAddress(request.getAddress())
                .setLocation(buildGeoLocationFromRequest(request.getLocation().getLatitude(), request.getLocation().getLongitude()));
    }

    private Mono<AsyncOperationResponseDto> publishEvent(SiteEventType eventType, String id, Site payload) {
        if (eventType != SiteEventType.DELETE && Objects.isNull(payload))
            return Mono.error(new IllegalArgumentException("Payload cannot be null for event type: " + eventType));

        var messageBuilder = PubsubMessage.newBuilder()
                .putAttributes("event_type", eventType.name())
                .putAttributes("entity_id", id);

        if (eventType != SiteEventType.DELETE)
            messageBuilder.setData(payload.toByteString());

        return Mono.fromFuture(publisherTemplate.publish(siteEventsTopic, messageBuilder.build()))
                .transformDeferred(RetryOperator.of(retryPubSub))
                .transformDeferred(mono -> fallbackCircuitBreaker(mono, cbPubSub, CB_PUBSUB_ID, eventType.name()))
                .map(messageId -> createAsyncOperationResponseDto(messageId, id));
    }

    private AsyncOperationResponseDto createAsyncOperationResponseDto(String messageId, String id) {
        var asyncResponse = new AsyncOperationResponseDto();
        asyncResponse.setId(UUID.fromString(id));
        asyncResponse.setStatus(AsyncOperationResponseDto.StatusEnum.ACCEPTED);
        asyncResponse.setMessage("Request queued. Trace ID: " + messageId);
        return asyncResponse;
    }

    private Mono<SiteResponseDto> getSiteFromDb(UUID id) {
        return siteRepository
                .findById(id.toString())
                .transformDeferred(RetryOperator.of(retryFirestore))
                .transformDeferred(mono-> fallbackCircuitBreaker(mono, cbFirestore, CB_FIRESTORE_ID, "findById"))
                .map(siteMapper::toDto)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("Site with id %s not found", id))));
    }

    private Mono<ListSitesResponseDto> getSitesByUserIdFromDb(UUID userId, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var countByUserId = siteRepository.countByUserId(userId.toString())
                .transformDeferred(RetryOperator.of(retryFirestore))
                .transformDeferred(mono-> fallbackCircuitBreaker(mono, cbFirestore, CB_FIRESTORE_ID, "countByUserId"));

        var sitesList = siteRepository.findAllByUserId(userId.toString(), pageable)
                .transformDeferred(RetryOperator.of(retryFirestore))
                .transformDeferred(flux-> fallbackCircuitBreaker(flux, cbFirestore, CB_FIRESTORE_ID, "findAllByUserId"))
                .map(siteMapper::toDto)
                .collectList();

        return Mono.zip(countByUserId, sitesList)
                .map(tuple -> {
                    var totalElements = tuple.getT1();
                    var sites = tuple.getT2();
                    var listSitesResponseDto = new ListSitesResponseDto();
                    listSitesResponseDto.setTotalCount(Math.toIntExact(totalElements));
                    listSitesResponseDto.setSites(sites);
                    listSitesResponseDto.setTotalPages((int) Math.ceil((double) totalElements / size));
                    return listSitesResponseDto;
                });
    }

    private <T> Mono<T> fallbackCircuitBreaker(Mono<T> it, ReactiveCircuitBreaker cb, String cbId,  String eventType){
        return cb.run(it, throwable -> {
            log.error("{} Circuit Breaker is open for event type {}. Fallback initiated.", cbId, eventType, throwable);
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service is temporarily unavailable."));
        });
    }

    private <T> Flux<T> fallbackCircuitBreaker(Flux<T> it, ReactiveCircuitBreaker cb, String cbId, String eventType){
        return cb.run(it, throwable -> {
            log.error("{} Circuit Breaker is open for event type {}. Fallback initiated.", cbId, eventType, throwable);
            return Flux.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service is temporarily unavailable."));
        });
    }
}
