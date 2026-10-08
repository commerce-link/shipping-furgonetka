package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

class PickupDateProposalsRequest {

    @JsonProperty("packages")
    private final List<PackageId> packages;
    @JsonProperty("ready_date")
    private final String readyDate;
    @JsonProperty("days_ahead")
    private final int daysAhead;

    PickupDateProposalsRequest(List<PackageId> packages, String readyDate, int daysAhead) {
        this.packages = packages;
        this.readyDate = readyDate;
        this.daysAhead = daysAhead;
    }
}
