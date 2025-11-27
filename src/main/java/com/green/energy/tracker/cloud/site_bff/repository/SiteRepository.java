package com.green.energy.tracker.cloud.site_bff.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.green.energy.tracker.cloud.site_bff.model.SiteReadDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SiteRepository extends FirestoreReactiveRepository<SiteReadDocument> {
    Flux<SiteReadDocument> findAllByUserId(String userId, Pageable pageable);
    Mono<Long> countByUserId(String userId);
}