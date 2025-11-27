package com.green.energy.tracker.cloud.site_bff.model;


import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.*;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collectionName = "sites")
public class SiteReadDocument {

    @DocumentId
    private String id;
    private String name;
    private String userId;
    private String address;
    private GeoLocationRead location;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
