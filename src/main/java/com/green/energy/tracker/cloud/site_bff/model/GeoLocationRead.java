package com.green.energy.tracker.cloud.site_bff.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoLocationRead {
    private double latitude;
    private double longitude;
}
