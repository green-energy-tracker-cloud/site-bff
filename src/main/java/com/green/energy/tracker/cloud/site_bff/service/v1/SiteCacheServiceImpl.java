package com.green.energy.tracker.cloud.site_bff.service.v1;

import com.green.energy.tracker.cloud.sitebff.web.model.ListSitesResponseDto;
import com.green.energy.tracker.cloud.sitebff.web.model.SiteResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteCacheServiceImpl implements SiteCacheService {

    @Value("${spring.data.redis.prefix-key}")
    private String prefixKey;
    @Value("${spring.data.redis.ttl-seconds}")
    private Integer cacheTtlSeconds;
    @Value("${pagination.default.page:0}")
    private int defaultPage;
    @Value("${pagination.default.size:10}")
    private int defaultPageSize;

    private final ReactiveRedisTemplate<String, SiteResponseDto> siteRedisTemplate;
    private final ReactiveRedisTemplate<String, ListSitesResponseDto> siteListRedisTemplate;

    @Override
    public Mono<SiteResponseDto> getSite(UUID id, Supplier<Mono<SiteResponseDto>> dbFallback) {
        String cacheKey = buildSiteCacheKey(id);
        return getFromCache(cacheKey, siteRedisTemplate, dbFallback, "site with id " + id);
    }

    @Override
    public Mono<ListSitesResponseDto> getSitesByUserId(UUID userId, Integer page, Integer size, Supplier<Mono<ListSitesResponseDto>> dbFallback) {
        int pageNum = (page != null && page >= 0) ? page : defaultPage;
        int pageSize = (size != null && size > 0) ? size : defaultPageSize;
        String cacheKey = buildUserSitesCacheKey(userId, pageNum, pageSize);
        return getFromCache(cacheKey, siteListRedisTemplate, dbFallback, "sites for user id " + userId);
    }

    private <T> Mono<T> getFromCache(String cacheKey, ReactiveRedisTemplate<String, T> template, Supplier<Mono<T>> fallbackSupplier, String logContext) {
        Mono<T> fallback = Mono.defer(fallbackSupplier).cache();
        return template
                .opsForValue()
                .get(cacheKey)
                .switchIfEmpty(
                        fallback.flatMap(dto ->
                                template.opsForValue()
                                        .set(cacheKey, dto, Duration.ofSeconds(cacheTtlSeconds))
                                        .thenReturn(dto)
                                        .onErrorResume(e -> {
                                            log.warn("Failed to cache {}. Reason: {}", logContext, e.getMessage());
                                            return Mono.just(dto);
                                        })
                        )
                )
                .onErrorResume(e -> {
                    log.warn("Failed to get from cache for {}. Falling back to DB. Reason: {}", logContext, e.getMessage());
                    return fallback;
                });
    }

    private String buildSiteCacheKey(UUID id) {
        return prefixKey + ":" + id.toString();
    }

    private String buildUserSitesCacheKey(UUID userId, int page, int size) {
        return prefixKey + ":user:" + userId + ":page:" + page + ":size:" + size;
    }
}
