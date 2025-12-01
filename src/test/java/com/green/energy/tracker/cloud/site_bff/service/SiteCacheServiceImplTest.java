package com.green.energy.tracker.cloud.site_bff.service;

import com.green.energy.tracker.cloud.sitebff.web.model.ListSitesResponseDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteResponseDto;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SiteCacheServiceImplTest {

    @Mock
    private ReactiveRedisTemplate<String, SiteResponseDto> siteRedisTemplate;

    @Mock
    private ReactiveRedisTemplate<String, ListSitesResponseDto> siteListRedisTemplate;

    @Mock
    private ReactiveValueOperations<String, SiteResponseDto> siteValueOps;

    @Mock
    private ReactiveValueOperations<String, ListSitesResponseDto> siteListValueOps;

    private SiteCacheServiceImpl siteCacheService;

    private Retry retryCache;

    private static final String PREFIX_KEY = "site";
    private static final Integer CACHE_TTL_SECONDS = 3600;
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 10;

    @BeforeEach
    void setUp() {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(100))
                .build();
        retryCache = Retry.of("cache", retryConfig);

        siteCacheService = new SiteCacheServiceImpl(siteRedisTemplate, siteListRedisTemplate, retryCache);

        ReflectionTestUtils.setField(siteCacheService, "prefixKey", PREFIX_KEY);
        ReflectionTestUtils.setField(siteCacheService, "cacheTtlSeconds", CACHE_TTL_SECONDS);
        ReflectionTestUtils.setField(siteCacheService, "defaultPage", DEFAULT_PAGE);
        ReflectionTestUtils.setField(siteCacheService, "defaultPageSize", DEFAULT_PAGE_SIZE);

        lenient().when(siteRedisTemplate.opsForValue()).thenReturn(siteValueOps);
        lenient().when(siteListRedisTemplate.opsForValue()).thenReturn(siteListValueOps);
    }

    @Test
    void getSite_WhenCacheHit_ShouldReturnCachedValue() {
        // Given
        UUID siteId = UUID.randomUUID();
        String cacheKey = PREFIX_KEY + ":" + siteId;
        SiteResponseDto cachedSite = createSiteResponseDto(siteId);

        when(siteValueOps.get(cacheKey)).thenReturn(Mono.just(cachedSite));

        Supplier<Mono<SiteResponseDto>> dbFallback = () -> Mono.error(new RuntimeException("Should not be called"));

        // When
        Mono<SiteResponseDto> result = siteCacheService.getSite(siteId, dbFallback);

        // Then
        StepVerifier.create(result)
                .expectNext(cachedSite)
                .verifyComplete();

        verify(siteValueOps).get(cacheKey);
        verify(siteValueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void getSite_WhenCacheMiss_ShouldFetchFromDbAndCache() {
        // Given
        UUID siteId = UUID.randomUUID();
        String cacheKey = PREFIX_KEY + ":" + siteId;
        SiteResponseDto dbSite = createSiteResponseDto(siteId);

        when(siteValueOps.get(cacheKey)).thenReturn(Mono.empty());
        when(siteValueOps.set(cacheKey, dbSite, Duration.ofSeconds(CACHE_TTL_SECONDS)))
                .thenReturn(Mono.just(true));

        Supplier<Mono<SiteResponseDto>> dbFallback = () -> Mono.just(dbSite);

        // When
        Mono<SiteResponseDto> result = siteCacheService.getSite(siteId, dbFallback);

        // Then
        StepVerifier.create(result)
                .expectNext(dbSite)
                .verifyComplete();

        verify(siteValueOps).get(cacheKey);
        verify(siteValueOps).set(cacheKey, dbSite, Duration.ofSeconds(CACHE_TTL_SECONDS));
    }

    @Test
    void getSite_WhenCacheGetFails_ShouldFallbackToDb() {
        // Given
        UUID siteId = UUID.randomUUID();
        String cacheKey = PREFIX_KEY + ":" + siteId;
        SiteResponseDto dbSite = createSiteResponseDto(siteId);

        when(siteValueOps.get(cacheKey)).thenReturn(Mono.error(new RedisConnectionFailureException("Connection failed")));

        Supplier<Mono<SiteResponseDto>> dbFallback = () -> Mono.just(dbSite);

        // When
        Mono<SiteResponseDto> result = siteCacheService.getSite(siteId, dbFallback);

        // Then
        StepVerifier.create(result)
                .expectNext(dbSite)
                .verifyComplete();

        verify(siteValueOps, atLeastOnce()).get(cacheKey);
    }

    @Test
    void getSite_WhenCacheMissAndCacheSetFails_ShouldReturnDbValue() {
        // Given
        UUID siteId = UUID.randomUUID();
        String cacheKey = PREFIX_KEY + ":" + siteId;
        SiteResponseDto dbSite = createSiteResponseDto(siteId);

        when(siteValueOps.get(cacheKey)).thenReturn(Mono.empty());
        when(siteValueOps.set(cacheKey, dbSite, Duration.ofSeconds(CACHE_TTL_SECONDS)))
                .thenReturn(Mono.error(new RedisConnectionFailureException("Cannot set cache")));

        Supplier<Mono<SiteResponseDto>> dbFallback = () -> Mono.just(dbSite);

        // When
        Mono<SiteResponseDto> result = siteCacheService.getSite(siteId, dbFallback);

        // Then
        StepVerifier.create(result)
                .expectNext(dbSite)
                .verifyComplete();

        verify(siteValueOps).get(cacheKey);
        verify(siteValueOps, atLeastOnce()).set(cacheKey, dbSite, Duration.ofSeconds(CACHE_TTL_SECONDS));
    }

    @Test
    void getSitesByUserId_WhenCacheHit_ShouldReturnCachedValue() {
        // Given
        UUID userId = UUID.randomUUID();
        Integer page = 0;
        Integer size = 10;
        String cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + size;
        ListSitesResponseDto cachedList = createListSitesResponseDto();

        when(siteListValueOps.get(cacheKey)).thenReturn(Mono.just(cachedList));

        Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.error(new RuntimeException("Should not be called"));

        // When
        Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, size, dbFallback);

        // Then
        StepVerifier.create(result)
                .expectNext(cachedList)
                .verifyComplete();

        verify(siteListValueOps).get(cacheKey);
        verify(siteListValueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void getSitesByUserId_WhenCacheMiss_ShouldFetchFromDbAndCache() {
        // Given
        UUID userId = UUID.randomUUID();
        Integer page = 0;
        Integer size = 10;
        String cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + size;
        ListSitesResponseDto dbList = createListSitesResponseDto();

        when(siteListValueOps.get(cacheKey)).thenReturn(Mono.empty());
        when(siteListValueOps.set(cacheKey, dbList, Duration.ofSeconds(CACHE_TTL_SECONDS)))
                .thenReturn(Mono.just(true));

        Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(dbList);

        // When
        Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, size, dbFallback);

        // Then
        StepVerifier.create(result)
                .expectNext(dbList)
                .verifyComplete();

        verify(siteListValueOps).get(cacheKey);
        verify(siteListValueOps).set(cacheKey, dbList, Duration.ofSeconds(CACHE_TTL_SECONDS));
    }

    @Test
    void getSitesByUserId_WithNullPage_ShouldUseDefaultPage() {
        // Given
        UUID userId = UUID.randomUUID();
        Integer page = null;
        Integer size = 10;
        String cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + DEFAULT_PAGE + ":size:" + size;
        ListSitesResponseDto dbList = createListSitesResponseDto();

        when(siteListValueOps.get(cacheKey)).thenReturn(Mono.empty());
        when(siteListValueOps.set(cacheKey, dbList, Duration.ofSeconds(CACHE_TTL_SECONDS)))
                .thenReturn(Mono.just(true));

        Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(dbList);

        // When
        Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, size, dbFallback);

        // Then
        StepVerifier.create(result)
                .expectNext(dbList)
                .verifyComplete();

        verify(siteListValueOps).get(cacheKey);
    }

    @Test
    void getSitesByUserId_WithNegativePage_ShouldUseDefaultPage() {
        // Given
        UUID userId = UUID.randomUUID();
        Integer page = -1;
        Integer size = 10;
        String cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + DEFAULT_PAGE + ":size:" + size;
        ListSitesResponseDto dbList = createListSitesResponseDto();

        when(siteListValueOps.get(cacheKey)).thenReturn(Mono.empty());
        when(siteListValueOps.set(cacheKey, dbList, Duration.ofSeconds(CACHE_TTL_SECONDS)))
                .thenReturn(Mono.just(true));

        Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(dbList);

        // When
        Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, size, dbFallback);

        // Then
        StepVerifier.create(result)
                .expectNext(dbList)
                .verifyComplete();

        verify(siteListValueOps).get(cacheKey);
    }

    @Test
    void getSitesByUserId_WithNullSize_ShouldUseDefaultSize() {
        // Given
        UUID userId = UUID.randomUUID();
        Integer page = 0;
        Integer size = null;
        String cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + DEFAULT_PAGE_SIZE;
        ListSitesResponseDto dbList = createListSitesResponseDto();

        when(siteListValueOps.get(cacheKey)).thenReturn(Mono.empty());
        when(siteListValueOps.set(cacheKey, dbList, Duration.ofSeconds(CACHE_TTL_SECONDS)))
                .thenReturn(Mono.just(true));

        Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(dbList);

        // When
        Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, size, dbFallback);

        // Then
        StepVerifier.create(result)
                .expectNext(dbList)
                .verifyComplete();

        verify(siteListValueOps).get(cacheKey);
    }

    @Test
    void getSitesByUserId_WithZeroSize_ShouldUseDefaultSize() {
        // Given
        UUID userId = UUID.randomUUID();
        Integer page = 0;
        Integer size = 0;
        String cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + DEFAULT_PAGE_SIZE;
        ListSitesResponseDto dbList = createListSitesResponseDto();

        when(siteListValueOps.get(cacheKey)).thenReturn(Mono.empty());
        when(siteListValueOps.set(cacheKey, dbList, Duration.ofSeconds(CACHE_TTL_SECONDS)))
                .thenReturn(Mono.just(true));

        Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(dbList);

        // When
        Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, size, dbFallback);

        // Then
        StepVerifier.create(result)
                .expectNext(dbList)
                .verifyComplete();

        verify(siteListValueOps).get(cacheKey);
    }

    @Test
    void getSitesByUserId_WhenCacheGetFails_ShouldFallbackToDb() {
        // Given
        UUID userId = UUID.randomUUID();
        Integer page = 0;
        Integer size = 10;
        String cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + size;
        ListSitesResponseDto dbList = createListSitesResponseDto();

        when(siteListValueOps.get(cacheKey)).thenReturn(Mono.error(new RedisConnectionFailureException("Connection failed")));

        Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(dbList);

        // When
        Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, size, dbFallback);

        // Then
        StepVerifier.create(result)
                .expectNext(dbList)
                .verifyComplete();

        verify(siteListValueOps, atLeastOnce()).get(cacheKey);
    }

    @Test
    void getSitesByUserId_WhenCacheMissAndCacheSetFails_ShouldReturnDbValue() {
        // Given
        UUID userId = UUID.randomUUID();
        Integer page = 0;
        Integer size = 10;
        String cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + size;
        ListSitesResponseDto dbList = createListSitesResponseDto();

        when(siteListValueOps.get(cacheKey)).thenReturn(Mono.empty());
        when(siteListValueOps.set(cacheKey, dbList, Duration.ofSeconds(CACHE_TTL_SECONDS)))
                .thenReturn(Mono.error(new RedisConnectionFailureException("Cannot set cache")));

        Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(dbList);

        // When
        Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, size, dbFallback);

        // Then
        StepVerifier.create(result)
                .expectNext(dbList)
                .verifyComplete();

        verify(siteListValueOps).get(cacheKey);
        verify(siteListValueOps, atLeastOnce()).set(cacheKey, dbList, Duration.ofSeconds(CACHE_TTL_SECONDS));
    }

    // Helper methods
    private SiteResponseDto createSiteResponseDto(UUID id) {
        SiteResponseDto dto = new SiteResponseDto();
        dto.setId(id);
        dto.setName("Test Site");
        dto.setAddress("123 Test St");
        return dto;
    }

    private ListSitesResponseDto createListSitesResponseDto() {
        ListSitesResponseDto dto = new ListSitesResponseDto();
        dto.setSites(List.of(createSiteResponseDto(UUID.randomUUID())));
        dto.setTotalCount(1);
        dto.setTotalPages(1);
        return dto;
    }
}
