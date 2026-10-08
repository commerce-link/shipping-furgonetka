package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class PickupCommandStatusResponse {

    @JsonProperty("status")
    private String status;
    @JsonProperty("errors")
    private List<Error> errors = new ArrayList<>();
    @JsonProperty("pickup_details")
    private List<Detail> details = new ArrayList<>();

    String getStatus() { return status; }

    List<Error> getErrors() { return errors == null ? List.of() : errors; }

    List<Detail> getDetails() { return details == null ? List.of() : details; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Detail {
        @JsonProperty("pickup_id")
        private String pickupId;
        @JsonProperty("package_ids")
        private List<String> packageIds = new ArrayList<>();

        String getPickupId() { return pickupId; }

        List<String> getPackageIds() { return packageIds == null ? List.of() : packageIds; }
    }
}
