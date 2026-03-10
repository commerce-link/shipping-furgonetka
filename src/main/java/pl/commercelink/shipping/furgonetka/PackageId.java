package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonProperty;

class PackageId {
    @JsonProperty("id")
    private String id;

    private PackageId() {
    }

    PackageId(String id) {
        this.id = id;
    }

    String getId() {
        return id;
    }
}
