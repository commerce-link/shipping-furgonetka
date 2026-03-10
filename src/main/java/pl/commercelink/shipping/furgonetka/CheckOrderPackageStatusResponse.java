package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class CheckOrderPackageStatusResponse {

    @JsonProperty("status")
    private String status;

    String getStatus() {
        return status;
    }
}
