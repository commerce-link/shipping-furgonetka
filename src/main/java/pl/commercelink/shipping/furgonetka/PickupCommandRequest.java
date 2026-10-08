package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

class PickupCommandRequest {

    @JsonProperty("packages")
    private final List<PackageId> packages;
    @JsonProperty("pickup_date")
    private final PickupDate pickupDate;

    PickupCommandRequest(List<PackageId> packages, PickupDate pickupDate) {
        this.packages = packages;
        this.pickupDate = pickupDate;
    }

    record PickupDate(@JsonProperty("date") String date, @JsonProperty("min_time") String minTime,
                      @JsonProperty("max_time") String maxTime, @JsonProperty("hash") String hash) {
    }
}
