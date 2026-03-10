package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

class OrderPickupRequest {

    @JsonProperty("packages")
    private List<PackageId> packages;

    List<PackageId> getPackages() {
        return packages;
    }

    void setPackages(List<PackageId> packages) {
        this.packages = packages;
    }
}