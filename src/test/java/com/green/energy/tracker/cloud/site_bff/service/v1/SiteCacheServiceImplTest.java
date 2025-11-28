package com.green.energy.tracker.cloud.site_bff.service.v1;

import com.green.energy.tracker.cloud.sitebff.web.model.ListSitesResponseDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SiteCacheServiceImpl Unit Tests")
class SiteCacheServiceImplTest {

    @Mock
    private ReactiveRedisTemplate<String, SiteResponseDto> siteRedisTemplate;

    @Mock
    private ReactiveRedisTemplate<String, ListSitesResponseDto> siteListRedisTemplate;

    @Mock
    private ReactiveValueOperations<String, SiteResponseDto> siteValueOperations;

    @Mock
    private ReactiveValueOperations<String, ListSitesResponseDto> listValueOperations;

    private SiteCacheServiceImpl siteCacheService;

    private static final String PREFIX_KEY = "site-bff";
    private static final Integer CACHE_TTL_SECONDS = 300;
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final Long MAX_ATTEMPTS = 3L;
    private static final Long MIN_BACKOFF_MILLIS = 100L;
    private static final Long MAX_BACKOFF_MILLIS = 1000L;

    @BeforeEach
    void setUp() {
        siteCacheService = new SiteCacheServiceImpl(siteRedisTemplate, siteListRedisTemplate);
        ReflectionTestUtils.setField(siteCacheService, "prefixKey", PREFIX_KEY);
        ReflectionTestUtils.setField(siteCacheService, "cacheTtlSeconds", CACHE_TTL_SECONDS);
        ReflectionTestUtils.setField(siteCacheService, "defaultPage", DEFAULT_PAGE);
        ReflectionTestUtils.setField(siteCacheService, "defaultPageSize", DEFAULT_PAGE_SIZE);
        ReflectionTestUtils.setField(siteCacheService, "maxAttempts", MAX_ATTEMPTS);
        ReflectionTestUtils.setField(siteCacheService, "minBackoffMillis", MIN_BACKOFF_MILLIS);
        ReflectionTestUtils.setField(siteCacheService, "maxBackoffMillis", MAX_BACKOFF_MILLIS);
    }

    @Nested
    @DisplayName("getSite Tests")
    class GetSiteTests {

        @Test
        @DisplayName("Should return site from cache when cache hit occurs")
        void getSite_shouldReturnFromCache_whenCacheHit() {
            var id = UUID.randomUUID();
            var cachedSite = createTestSiteResponseDto(id);
            var cacheKey = PREFIX_KEY + ":" + id;

            when(siteRedisTemplate.opsForValue()).thenReturn(siteValueOperations);
            when(siteValueOperations.get(cacheKey)).thenReturn(Mono.just(cachedSite));

            Supplier<Mono<SiteResponseDto>> dbFallback = () -> Mono.error(new RuntimeException("Should not call DB"));

            Mono<SiteResponseDto> result = siteCacheService.getSite(id, dbFallback);

            StepVerifier.create(result)
                    .expectNext(cachedSite)
                    .verifyComplete();

            verify(siteRedisTemplate).opsForValue();
            verify(siteValueOperations).get(cacheKey);
            verify(siteValueOperations, never()).set(anyString(), any(), any(Duration.class));
        }

        @Test
        @DisplayName("Should fetch from DB and cache when cache miss occurs")
        void getSite_shouldFetchFromDbAndCache_whenCacheMiss() {
            var id = UUID.randomUUID();
            var siteFromDb = createTestSiteResponseDto(id);
            var cacheKey = PREFIX_KEY + ":" + id;

            when(siteRedisTemplate.opsForValue()).thenReturn(siteValueOperations);
            when(siteValueOperations.get(cacheKey)).thenReturn(Mono.empty());
            when(siteValueOperations.set(eq(cacheKey), eq(siteFromDb), eq(Duration.ofSeconds(CACHE_TTL_SECONDS))))
                    .thenReturn(Mono.just(true));

            Supplier<Mono<SiteResponseDto>> dbFallback = () -> Mono.just(siteFromDb);

            Mono<SiteResponseDto> result = siteCacheService.getSite(id, dbFallback);

            StepVerifier.create(result)
                    .expectNext(siteFromDb)
                    .verifyComplete();

            verify(siteValueOperations).get(cacheKey);
            verify(siteValueOperations).set(cacheKey, siteFromDb, Duration.ofSeconds(CACHE_TTL_SECONDS));
        }

        @Test
        @DisplayName("Should return from DB when cache get operation fails")
        void getSite_shouldReturnFromDb_whenCacheGetFails() {
            var id = UUID.randomUUID();
            var siteFromDb = createTestSiteResponseDto(id);
            var cacheKey = PREFIX_KEY + ":" + id;

            when(siteRedisTemplate.opsForValue()).thenReturn(siteValueOperations);
            when(siteValueOperations.get(cacheKey)).thenReturn(Mono.error(new RedisConnectionFailureException("Redis error")));

            Supplier<Mono<SiteResponseDto>> dbFallback = () -> Mono.just(siteFromDb);

            Mono<SiteResponseDto> result = siteCacheService.getSite(id, dbFallback);

            StepVerifier.create(result)
                    .expectNext(siteFromDb)
                    .verifyComplete();

            verify(siteValueOperations).get(cacheKey);
        }

        @Test
        @DisplayName("Should return from DB when cache set operation fails")
        void getSite_shouldReturnFromDb_whenCacheSetFails() {
            var id = UUID.randomUUID();
            var siteFromDb = createTestSiteResponseDto(id);
            var cacheKey = PREFIX_KEY + ":" + id;

            when(siteRedisTemplate.opsForValue()).thenReturn(siteValueOperations);
            when(siteValueOperations.get(cacheKey)).thenReturn(Mono.empty());
            when(siteValueOperations.set(eq(cacheKey), eq(siteFromDb), eq(Duration.ofSeconds(CACHE_TTL_SECONDS))))
                    .thenReturn(Mono.error(new RedisConnectionFailureException("Cache set error")));

            Supplier<Mono<SiteResponseDto>> dbFallback = () -> Mono.just(siteFromDb);

            Mono<SiteResponseDto> result = siteCacheService.getSite(id, dbFallback);

            StepVerifier.create(result)
                    .expectNext(siteFromDb)
                    .verifyComplete();

            verify(siteValueOperations).get(cacheKey);
            verify(siteValueOperations).set(cacheKey, siteFromDb, Duration.ofSeconds(CACHE_TTL_SECONDS));
        }

        @Test
        @DisplayName("Should build correct cache key format")
        void getSite_shouldBuildCorrectCacheKey() {
            var id = UUID.randomUUID();
            var expectedKey = PREFIX_KEY + ":" + id;
            var siteFromDb = createTestSiteResponseDto(id);

            when(siteRedisTemplate.opsForValue()).thenReturn(siteValueOperations);
            when(siteValueOperations.get(expectedKey)).thenReturn(Mono.empty());
            when(siteValueOperations.set(eq(expectedKey), any(), any())).thenReturn(Mono.just(true));

            Supplier<Mono<SiteResponseDto>> dbFallback = () -> Mono.just(siteFromDb);

            siteCacheService.getSite(id, dbFallback).block();

            verify(siteValueOperations).get(expectedKey);
            verify(siteValueOperations).set(eq(expectedKey), any(), any());
        }

        @Test
        @DisplayName("Should not invoke DB fallback when cache returns data")
        void getSite_shouldNotInvokeDbFallback_whenCacheHit() {
            var id = UUID.randomUUID();
            var cachedSite = createTestSiteResponseDto(id);
            var cacheKey = PREFIX_KEY + ":" + id;
            AtomicInteger fallbackCallCount = new AtomicInteger(0);

            when(siteRedisTemplate.opsForValue()).thenReturn(siteValueOperations);
            when(siteValueOperations.get(cacheKey)).thenReturn(Mono.just(cachedSite));

            Supplier<Mono<SiteResponseDto>> dbFallback = () -> {
                fallbackCallCount.incrementAndGet();
                return Mono.just(createTestSiteResponseDto(id));
            };

            Mono<SiteResponseDto> result = siteCacheService.getSite(id, dbFallback);

            StepVerifier.create(result)
                    .expectNext(cachedSite)
                    .verifyComplete();

            assertThat(fallbackCallCount.get()).isZero();
        }
    }

    @Nested
    @DisplayName("getSitesByUserId Tests")
    class GetSitesByUserIdTests {

        @Test
        @DisplayName("Should return sites from cache when cache hit occurs")
        void getSitesByUserId_shouldReturnFromCache_whenCacheHit() {
            var userId = UUID.randomUUID();
            var page = 0;
            var size = 10;
            var cachedList = createTestListSitesResponseDto();
            var cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + size;

            when(siteListRedisTemplate.opsForValue()).thenReturn(listValueOperations);
            when(listValueOperations.get(cacheKey)).thenReturn(Mono.just(cachedList));

            Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.error(new RuntimeException("Should not call DB"));

            Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, size, dbFallback);

            StepVerifier.create(result)
                    .expectNext(cachedList)
                    .verifyComplete();

            verify(listValueOperations).get(cacheKey);
            verify(listValueOperations, never()).set(anyString(), any(), any(Duration.class));
        }

        @Test
        @DisplayName("Should fetch from DB and cache when cache miss occurs")
        void getSitesByUserId_shouldFetchFromDbAndCache_whenCacheMiss() {
            var userId = UUID.randomUUID();
            var page = 0;
            var size = 10;
            var listFromDb = createTestListSitesResponseDto();
            var cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + size;

            when(siteListRedisTemplate.opsForValue()).thenReturn(listValueOperations);
            when(listValueOperations.get(cacheKey)).thenReturn(Mono.empty());
            when(listValueOperations.set(eq(cacheKey), eq(listFromDb), eq(Duration.ofSeconds(CACHE_TTL_SECONDS))))
                    .thenReturn(Mono.just(true));

            Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(listFromDb);

            Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, size, dbFallback);

            StepVerifier.create(result)
                    .expectNext(listFromDb)
                    .verifyComplete();

            verify(listValueOperations).get(cacheKey);
            verify(listValueOperations).set(cacheKey, listFromDb, Duration.ofSeconds(CACHE_TTL_SECONDS));
        }

        @Test
        @DisplayName("Should use default page when page parameter is null")
        void getSitesByUserId_shouldUseDefaultPage_whenPageIsNull() {
            var userId = UUID.randomUUID();
            var listFromDb = createTestListSitesResponseDto();
            var cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + DEFAULT_PAGE + ":size:" + DEFAULT_PAGE_SIZE;

            when(siteListRedisTemplate.opsForValue()).thenReturn(listValueOperations);
            when(listValueOperations.get(cacheKey)).thenReturn(Mono.empty());
            when(listValueOperations.set(eq(cacheKey), eq(listFromDb), eq(Duration.ofSeconds(CACHE_TTL_SECONDS))))
                    .thenReturn(Mono.just(true));

            Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(listFromDb);

            Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, null, null, dbFallback);

            StepVerifier.create(result)
                    .expectNext(listFromDb)
                    .verifyComplete();

            verify(listValueOperations).get(cacheKey);
        }

        @Test
        @DisplayName("Should use default page when page parameter is negative")
        void getSitesByUserId_shouldUseDefaultPage_whenPageIsNegative() {
            var userId = UUID.randomUUID();
            var listFromDb = createTestListSitesResponseDto();
            var cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + DEFAULT_PAGE + ":size:" + DEFAULT_PAGE_SIZE;

            when(siteListRedisTemplate.opsForValue()).thenReturn(listValueOperations);
            when(listValueOperations.get(cacheKey)).thenReturn(Mono.empty());
            when(listValueOperations.set(eq(cacheKey), eq(listFromDb), eq(Duration.ofSeconds(CACHE_TTL_SECONDS))))
                    .thenReturn(Mono.just(true));

            Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(listFromDb);

            Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, -1, null, dbFallback);

            StepVerifier.create(result)
                    .expectNext(listFromDb)
                    .verifyComplete();

            verify(listValueOperations).get(cacheKey);
        }

        @Test
        @DisplayName("Should use default size when size parameter is null")
        void getSitesByUserId_shouldUseDefaultSize_whenSizeIsNull() {
            var userId = UUID.randomUUID();
            var page = 1;
            var listFromDb = createTestListSitesResponseDto();
            var cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + DEFAULT_PAGE_SIZE;

            when(siteListRedisTemplate.opsForValue()).thenReturn(listValueOperations);
            when(listValueOperations.get(cacheKey)).thenReturn(Mono.empty());
            when(listValueOperations.set(eq(cacheKey), eq(listFromDb), eq(Duration.ofSeconds(CACHE_TTL_SECONDS))))
                    .thenReturn(Mono.just(true));

            Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(listFromDb);

            Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, null, dbFallback);

            StepVerifier.create(result)
                    .expectNext(listFromDb)
                    .verifyComplete();

            verify(listValueOperations).get(cacheKey);
        }

        @Test
        @DisplayName("Should use default size when size parameter is zero")
        void getSitesByUserId_shouldUseDefaultSize_whenSizeIsZero() {
            var userId = UUID.randomUUID();
            var page = 1;
            var listFromDb = createTestListSitesResponseDto();
            var cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + DEFAULT_PAGE_SIZE;

            when(siteListRedisTemplate.opsForValue()).thenReturn(listValueOperations);
            when(listValueOperations.get(cacheKey)).thenReturn(Mono.empty());
            when(listValueOperations.set(eq(cacheKey), eq(listFromDb), eq(Duration.ofSeconds(CACHE_TTL_SECONDS))))
                    .thenReturn(Mono.just(true));

            Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(listFromDb);

            Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, 0, dbFallback);

            StepVerifier.create(result)
                    .expectNext(listFromDb)
                    .verifyComplete();

            verify(listValueOperations).get(cacheKey);
        }

        @Test
        @DisplayName("Should use default size when size parameter is negative")
        void getSitesByUserId_shouldUseDefaultSize_whenSizeIsNegative() {
            var userId = UUID.randomUUID();
            var page = 1;
            var listFromDb = createTestListSitesResponseDto();
            var cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + DEFAULT_PAGE_SIZE;

            when(siteListRedisTemplate.opsForValue()).thenReturn(listValueOperations);
            when(listValueOperations.get(cacheKey)).thenReturn(Mono.empty());
            when(listValueOperations.set(eq(cacheKey), eq(listFromDb), eq(Duration.ofSeconds(CACHE_TTL_SECONDS))))
                    .thenReturn(Mono.just(true));

            Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(listFromDb);

            Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, -5, dbFallback);

            StepVerifier.create(result)
                    .expectNext(listFromDb)
                    .verifyComplete();

            verify(listValueOperations).get(cacheKey);
        }

        @Test
        @DisplayName("Should return from DB when cache get operation fails")
        void getSitesByUserId_shouldReturnFromDb_whenCacheGetFails() {
            var userId = UUID.randomUUID();
            var page = 0;
            var size = 10;
            var listFromDb = createTestListSitesResponseDto();
            var cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + size;

            when(siteListRedisTemplate.opsForValue()).thenReturn(listValueOperations);
            when(listValueOperations.get(cacheKey)).thenReturn(Mono.error(new RedisConnectionFailureException("Redis error")));

            Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(listFromDb);

            Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, size, dbFallback);

            StepVerifier.create(result)
                    .expectNext(listFromDb)
                    .verifyComplete();

            verify(listValueOperations).get(cacheKey);
        }

        @Test
        @DisplayName("Should return from DB when cache set operation fails")
        void getSitesByUserId_shouldReturnFromDb_whenCacheSetFails() {
            var userId = UUID.randomUUID();
            var page = 0;
            var size = 10;
            var listFromDb = createTestListSitesResponseDto();
            var cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + size;

            when(siteListRedisTemplate.opsForValue()).thenReturn(listValueOperations);
            when(listValueOperations.get(cacheKey)).thenReturn(Mono.empty());
            when(listValueOperations.set(eq(cacheKey), eq(listFromDb), eq(Duration.ofSeconds(CACHE_TTL_SECONDS))))
                    .thenReturn(Mono.error(new RedisConnectionFailureException("Cache set error")));

            Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(listFromDb);

            Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, size, dbFallback);

            StepVerifier.create(result)
                    .expectNext(listFromDb)
                    .verifyComplete();

            verify(listValueOperations).get(cacheKey);
            verify(listValueOperations).set(cacheKey, listFromDb, Duration.ofSeconds(CACHE_TTL_SECONDS));
        }

        @Test
        @DisplayName("Should build correct cache key format with pagination")
        void getSitesByUserId_shouldBuildCorrectCacheKey() {
            var userId = UUID.randomUUID();
            var page = 2;
            var size = 20;
            var expectedKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + size;
            var listFromDb = createTestListSitesResponseDto();

            when(siteListRedisTemplate.opsForValue()).thenReturn(listValueOperations);
            when(listValueOperations.get(expectedKey)).thenReturn(Mono.empty());
            when(listValueOperations.set(eq(expectedKey), any(), any())).thenReturn(Mono.just(true));

            Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(listFromDb);

            siteCacheService.getSitesByUserId(userId, page, size, dbFallback).block();

            verify(listValueOperations).get(expectedKey);
            verify(listValueOperations).set(eq(expectedKey), any(), any());
        }

        @Test
        @DisplayName("Should create different cache keys for different pagination parameters")
        void getSitesByUserId_shouldCreateDifferentKeysForDifferentPagination() {
            var userId = UUID.randomUUID();
            var listFromDb = createTestListSitesResponseDto();

            when(siteListRedisTemplate.opsForValue()).thenReturn(listValueOperations);
            when(listValueOperations.get(anyString())).thenReturn(Mono.empty());
            when(listValueOperations.set(anyString(), any(), any())).thenReturn(Mono.just(true));

            Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> Mono.just(listFromDb);

            siteCacheService.getSitesByUserId(userId, 0, 10, dbFallback).block();
            siteCacheService.getSitesByUserId(userId, 1, 10, dbFallback).block();
            siteCacheService.getSitesByUserId(userId, 0, 20, dbFallback).block();

            var key1 = PREFIX_KEY + ":user:" + userId + ":page:0:size:10";
            var key2 = PREFIX_KEY + ":user:" + userId + ":page:1:size:10";
            var key3 = PREFIX_KEY + ":user:" + userId + ":page:0:size:20";

            verify(listValueOperations).get(key1);
            verify(listValueOperations).get(key2);
            verify(listValueOperations).get(key3);
        }

        @Test
        @DisplayName("Should not invoke DB fallback when cache returns data")
        void getSitesByUserId_shouldNotInvokeDbFallback_whenCacheHit() {
            var userId = UUID.randomUUID();
            var page = 0;
            var size = 10;
            var cachedList = createTestListSitesResponseDto();
            var cacheKey = PREFIX_KEY + ":user:" + userId + ":page:" + page + ":size:" + size;
            AtomicInteger fallbackCallCount = new AtomicInteger(0);

            when(siteListRedisTemplate.opsForValue()).thenReturn(listValueOperations);
            when(listValueOperations.get(cacheKey)).thenReturn(Mono.just(cachedList));

            Supplier<Mono<ListSitesResponseDto>> dbFallback = () -> {
                fallbackCallCount.incrementAndGet();
                return Mono.just(createTestListSitesResponseDto());
            };

            Mono<ListSitesResponseDto> result = siteCacheService.getSitesByUserId(userId, page, size, dbFallback);

            StepVerifier.create(result)
                    .expectNext(cachedList)
                    .verifyComplete();

            assertThat(fallbackCallCount.get()).isZero();
        }
    }

    private SiteResponseDto createTestSiteResponseDto(UUID id) {
        var siteResponseDto = new SiteResponseDto();
        siteResponseDto.setId(id);
        siteResponseDto.setName("Test Site");
        siteResponseDto.setAddress("123 Test Street");
        siteResponseDto.setUserId(UUID.randomUUID());
        return siteResponseDto;
    }

    private ListSitesResponseDto createTestListSitesResponseDto() {
        var listSitesResponseDto = new ListSitesResponseDto();
        listSitesResponseDto.setTotalCount(1);
        listSitesResponseDto.setTotalPages(1);
        listSitesResponseDto.setSites(List.of(createTestSiteResponseDto(UUID.randomUUID())));
        return listSitesResponseDto;
    }
}
