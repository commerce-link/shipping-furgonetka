package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class OrderPackageResponse {

    @JsonProperty("uuid")
    private String uuid;

    // Getter and Setter
    String getUuid() {
        return uuid;
    }
}